package com.waz.service

import com.waz.api.impl.ErrorResponse
import com.waz.content.Preferences.Preference.PrefCodec.LegalHoldRequestCodec
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage, UserPreferences}
import com.waz.model._
import com.waz.model.otr.Client.DeviceClass
import com.waz.model.otr.{ClientId, UserClients}
import com.waz.service.EventScheduler.Stage
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.handler.LegalHoldError
import com.wire.cryptobox.CryptoBox
import com.wire.signals.Signal

import scala.concurrent.Future
import scala.util.Try

trait LegalHoldService {
  def legalHoldEventStage: Stage.Atomic
  def isLegalHoldActive(userId: UserId): Signal[Boolean]
  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean]
  def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]]
  def legalHoldRequest: Signal[Option[LegalHoldRequest]]
  def getFingerprint(request: LegalHoldRequest): Option[String]
  def deleteLegalHoldRequest(): Future[Unit]
  def storeLegalHoldRequest(request: LegalHoldRequest): Future[Unit]
  def approveRequest(request: LegalHoldRequest,
                     password: Option[String]): Future[Either[LegalHoldError, Unit]]
  def messageEventStage: Stage.Atomic
  def updateLegalHoldStatusAfterFetchingClients(clients: Seq[UserClients]): Future[Unit]
}

class LegalHoldServiceImpl(selfUserId: UserId,
                           teamId: Option[TeamId],
                           userPrefs: UserPreferences,
                           client: LegalHoldClient,
                           clientsService: OtrClientsService,
                           clientsStorage: OtrClientsStorage,
                           convsStorage: ConversationStorage,
                           membersStorage: MembersStorage,
                           cryptoSessionService: CryptoSessionService,
                           sync: SyncServiceHandle) extends LegalHoldService {

  import com.waz.threading.Threading.Implicits.Background

  override def legalHoldEventStage: Stage.Atomic = EventScheduler.Stage[LegalHoldEvent] { (_, events) =>
    Future.traverse(events) {
      case LegalHoldRequestEvent(userId, request) if userId == selfUserId =>
        storeLegalHoldRequest(request)
      case LegalHoldEnableEvent(userId) if userId == selfUserId =>
        deleteLegalHoldRequest()
      case LegalHoldDisableEvent(userId) if userId == selfUserId =>
        deleteLegalHoldRequest()
      case _ =>
        Future.successful({})
    }
  }

  override def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    clientsStorage.optSignal(userId).map(_.fold(false)(_.clients.values.exists(_.isLegalHoldDevice)))

  override def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    convsStorage.optSignal(conversationId).map(_.fold(false)(_.isUnderLegalHold))

  override def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]] = for {
    users             <- membersStorage.activeMembers(conversationId)
    usersAndStatus    <- Signal.sequence(users.map(userId => isLegalHoldActive(userId).map(active => userId -> active)).toSeq: _*)
    legalHoldSubjects = usersAndStatus.collect { case (userId, true) => userId }
  } yield legalHoldSubjects

  private lazy val legalHoldRequestPref = userPrefs(UserPreferences.LegalHoldRequest)

  override def legalHoldRequest: Signal[Option[LegalHoldRequest]] = legalHoldRequestPref.signal

  override def getFingerprint(request: LegalHoldRequest): Option[String] =
    Try(CryptoBox.getFingerprintFromPrekey(request.lastPreKey)).toOption.map(new String(_))

  def approveRequest(request: LegalHoldRequest,
                     password: Option[String]): Future[Either[LegalHoldError, Unit]] = for {
    _      <- createLegalHoldClientAndSession(request)
    result <- postApproval(password)
    _      <- result match {
      case Left(_) => deleteLegalHoldClientAndSession(request.clientId)
      case Right(_) => deleteLegalHoldRequest()
    }
  } yield result

  private def postApproval(password: Option[String]): Future[Either[LegalHoldError, Unit]] = teamId match {
    case None =>
      Future.successful(Left(LegalHoldError.NotInTeam))
    case Some(teamId) =>
      client.approveRequest(teamId, selfUserId, password).future.map {
        case Left(ErrorResponse(_, _, label)) if label == "access-denied" || label == "invalid-payload" =>
          Left(LegalHoldError.InvalidPassword)
        case Left(_) =>
          Left(LegalHoldError.InvalidResponse)
        case Right(_) =>
          Right(())
    }
  }

  private def createLegalHoldClientAndSession(request: LegalHoldRequest): Future[Unit] = for {
    client          <- clientsService.getOrCreateClient(selfUserId, request.clientId)
    legalHoldClient = client.copy(deviceClass = DeviceClass.LegalHold)
    _               <- clientsService.updateUserClients(selfUserId, Seq(legalHoldClient), replace = false)
    sessionId       = SessionId(selfUserId, legalHoldClient.id)
    _               <- cryptoSessionService.getOrCreateSession(sessionId, request.lastPreKey)
  } yield ()

  private def deleteLegalHoldClientAndSession(clientId: ClientId): Future[Unit] = for {
    _ <- clientsService.removeClients(selfUserId, Seq(clientId))
    _ <- cryptoSessionService.deleteSession(SessionId(selfUserId, clientId))
  } yield ()

  def storeLegalHoldRequest(request: LegalHoldRequest): Future[Unit] =
    legalHoldRequestPref := Some(request)

  def deleteLegalHoldRequest(): Future[Unit] =
    legalHoldRequestPref := None


  // --------------------------------------------------------------
  // Legal hold status & verification

  // The status should be recomputed when the participants change. There are
  // two ways we do this, 1) reacting to changes in the db due to normal client
  // discovery, and 2) explicitly syncing with the backend discover legal hold
  // devices. While 2) is ongoing, we prevent 1) from occurring, so we don't
  // try to compute the status with incomplete data.

  // To prevent update the legal hold status if we are still gathering
  // information about which clients were added/deleted.
  var isVerifyingLegalHold: Boolean = false

  // When clients are added, updated, or deleted...
  clientsStorage.onChanged.foreach { clients =>
    if (!isVerifyingLegalHold) onClientsChanged(clients)
  }

  // When a participant is added...
  membersStorage.onAdded.foreach { members =>
    if (!isVerifyingLegalHold) updateLegalHoldStatus(members.map(_.convId))
  }

  // When a participant is removed...
  membersStorage.onDeleted.foreach { members =>
    if (!isVerifyingLegalHold) updateLegalHoldStatus(members.map(_._2))
  }

  def onClientsChanged(userClients: Seq[UserClients]): Future[Unit] = {
    val userIds = userClients.map(_.id)

    for {
      convs <- Future.traverse(userIds)(membersStorage.getActiveConvs).map(_.flatten)
      _     <- updateLegalHoldStatus(convs)
    } yield ()
  }

  def updateLegalHoldStatus(convIds: Seq[ConvId]): Future[Unit] =
    Future.traverse(convIds.distinct)(updateLegalHoldStatus).map(_ => ())

  private def updateLegalHoldStatus(convId: ConvId): Future[Unit] = {
    for {
      participants      <- membersStorage.getActiveUsers(convId)
      clients           <- Future.traverse(participants)(clientsStorage.getClients).map(_.flatten)
      detectedLegalHold = clients.exists(_.isLegalHoldDevice)
      _                 <- convsStorage.updateAll2(Seq(convId), _.withNewLegalHoldStatus(detectedLegalHold))
    } yield ()
  }

  override def messageEventStage: Stage.Atomic = EventScheduler.Stage[MessageEvent] { (_, events) =>
    Future.traverse(events) {
      case GenericMessageEvent(convId, _, _, content) =>
        updateStatusFromMessageHint(convId, content.legalHoldStatus)
      case _ =>
        Future.successful(())
    }
  }

  private def updateStatusFromMessageHint(convId: RConvId,
                                          messageStatus: Messages.LegalHoldStatus): Future[Unit] = {
    (for {
      Some(conv)   <- convsStorage.getByRemoteId(convId)
      Some(update) = statusUpdate(conv, messageStatus)
      _            <- convsStorage.update(conv.id, update)
      _            <- verifyLegalHold(convId)
    } yield ()).fallbackTo(Future.successful(()))
  }

  private def statusUpdate(conv: ConversationData,
                           messageStatus: Messages.LegalHoldStatus): Option[ConversationData => ConversationData] =
    (conv.isUnderLegalHold, messageStatus) match {
      case (false, Messages.LegalHoldStatus.ENABLED) =>
        Some(_.withNewLegalHoldStatus(detectedLegalHoldDevice = true))
      case (true, Messages.LegalHoldStatus.DISABLED) =>
        Some(_.withNewLegalHoldStatus(detectedLegalHoldDevice = false))
      case _ =>
        None
    }

  private def verifyLegalHold(convId: RConvId): Future[Unit] = {
    isVerifyingLegalHold = true
    sync.syncClientsForLegalHold(convId).map(_ => ())
  }

  override def updateLegalHoldStatusAfterFetchingClients(clients: Seq[UserClients]): Future[Unit] = {
    (if (clients.nonEmpty) onClientsChanged(clients) else Future.successful(())).map { _ =>
      isVerifyingLegalHold = false
    }
  }

}

