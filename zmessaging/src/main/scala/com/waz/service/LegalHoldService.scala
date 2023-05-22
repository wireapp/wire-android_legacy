package com.waz.service

import com.waz.api.impl.ErrorResponse
import com.waz.content.Preferences.Preference.PrefCodec.LegalHoldRequestCodec
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.LegalHoldStatus
import com.waz.model._
import com.waz.model.otr.Client.DeviceClass
import com.waz.model.otr.{Client, ClientId}
import com.waz.service.EventScheduler.Stage
import com.waz.service.messages.MessagesService
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.handler.LegalHoldError
import com.wire.cryptobox.CryptoBox
import com.wire.signals.Signal

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait LegalHoldService {
  def legalHoldEventStage: Stage.Atomic
  def isLegalHoldActiveForSelfUser: Signal[Boolean]
  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean]
  def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]]
  def legalHoldRequest: Signal[Option[LegalHoldRequest]]
  def getFingerprint(request: LegalHoldRequest): Option[String]
  def onLegalHoldRequestSynced(request: Option[LegalHoldRequest]): Future[Unit]
  def approveRequest(request: LegalHoldRequest,
                     password: Option[String]): Future[Either[LegalHoldError, Unit]]
  def messageEventStage: Stage.Atomic
  def updateLegalHoldStatusAfterFetchingClients(): Unit
}

class LegalHoldServiceImpl(selfUserId: UserId,
                           selfDomain: Domain,
                           teamId: Option[TeamId],
                           userPrefs: UserPreferences,
                           client: LegalHoldClient,
                           clientsService: OtrClientsService,
                           clientsStorage: OtrClientsStorage,
                           convsStorage: ConversationStorage,
                           membersStorage: MembersStorage,
                           cryptoSessionService: CryptoSessionService,
                           sync: SyncServiceHandle,
                           messagesService: MessagesService,
                           userService: UserService
                          ) extends LegalHoldService with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override def legalHoldEventStage: Stage.Atomic = EventScheduler.Stage[LegalHoldEvent] ({ (_, events, tag) =>
    verbose(l"SSSTAGES<TAG:$tag> LegalHoldServiceImpl stage 1")
    Future.traverse(events)(processEvent)
  },
    name = "LegalHoldService - LegalHoldEvent"
  )

  private def processEvent(event: LegalHoldEvent): Future[Unit] = event match {
    case LegalHoldRequestEvent(userId, request) if userId == selfUserId =>
      storeLegalHoldRequest(request)

    case LegalHoldEnableEvent(userId) =>
      if (userId == selfUserId) onLegalHoldApprovedFromAnotherDevice()
      else userService.syncClients(userId).map(_ => ())


    case LegalHoldDisableEvent(userId) =>
      if (userId == selfUserId) onLegalHoldDisabled()
      else userService.syncClients(userId).map(_ => ())

    case _ =>
      Future.successful({})
  }

  override def isLegalHoldActiveForSelfUser: Signal[Boolean] = isLegalHoldActive(selfUserId)

  private def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    clientsStorage.optSignal(userId).map(_.fold(false)(_.clients.values.exists(_.isLegalHoldDevice)))

  override def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    convsStorage.optSignal(conversationId).map(_.fold(false)(_.isUnderLegalHold))

  override def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]] = for {
    users             <- membersStorage.activeMembers(conversationId)
    usersAndStatus    <- Signal.sequence(users.map(userId => isLegalHoldActive(userId).map(active => userId -> active)).toSeq: _*)
    legalHoldSubjects = usersAndStatus.collect { case (userId, true) => userId }
  } yield legalHoldSubjects

  private lazy val legalHoldRequestPref = userPrefs(UserPreferences.LegalHoldRequest)
  private lazy val legalHoldDisclosurePref = userPrefs(UserPreferences.LegalHoldDisclosureType)

  override def legalHoldRequest: Signal[Option[LegalHoldRequest]] = legalHoldRequestPref.signal

  override def getFingerprint(request: LegalHoldRequest): Option[String] =
    Try(CryptoBox.getFingerprintFromPrekey(request.lastPreKey)).toOption.map(new String(_))

  def approveRequest(request: LegalHoldRequest,
                     password: Option[String]): Future[Either[LegalHoldError, Unit]] = for {
    _      <- createLegalHoldClientAndSession(request)
    result <- postApproval(password)
    _      <- result match {
      case Left(_) =>
        deleteLegalHoldClientAndSession(request.clientId)
      case Right(_) =>
        for {
          Some(client)    <- clientsService.getClient(selfUserId, request.clientId)
          legalHoldClient = client.copy(isTemporary = false)
          _               <- clientsService.updateUserClients(selfUserId, Seq(legalHoldClient), replace = false)
          _               <- deleteLegalHoldRequest()
        } yield ()
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
    legalHoldClient = client.copy(deviceClass = DeviceClass.LegalHold, isTemporary = true)
    _               <- clientsService.updateUserClients(selfUserId, Seq(legalHoldClient), replace = false)
    sessionId       = SessionId(selfUserId, selfDomain, legalHoldClient.id)
    _               <- cryptoSessionService.getOrCreateSession(sessionId, request.lastPreKey)
  } yield ()

  private def deleteLegalHoldClientAndSession(clientId: ClientId): Future[Unit] = for {
    _ <- clientsService.removeClients(selfUserId, Set(clientId))
    _ <- cryptoSessionService.deleteSession(SessionId(selfUserId, selfDomain, clientId))
  } yield ()

  private def onLegalHoldApprovedFromAnotherDevice(): Future[Unit] = for {
    _ <- deleteLegalHoldRequest()
    _ <- legalHoldDisclosurePref := Some(LegalHoldStatus.Enabled)
  } yield()

  private def onLegalHoldDisabled(): Future[Unit] = for {
    _ <- deleteLegalHoldRequest()
    _ <- legalHoldDisclosurePref := Some(LegalHoldStatus.Disabled)
  } yield()

  override def onLegalHoldRequestSynced(request: Option[LegalHoldRequest]): Future[Unit] =
    request match {
      case Some(request) => storeLegalHoldRequest(request)
      case None =>
        for {
          _        <- deleteLegalHoldRequest()
          isActive <- isLegalHoldActiveForSelfUser.head
          _        <- if (isActive) legalHoldDisclosurePref := Some(LegalHoldStatus.Enabled)
                      else Future.successful(())
        } yield ()
    }

  private def storeLegalHoldRequest(request: LegalHoldRequest): Future[Unit] =
    legalHoldRequestPref := Some(request)

  private def deleteLegalHoldRequest(): Future[Unit] =
    legalHoldRequestPref := None


  // --------------------------------------------------------------
  // Legal hold status & verification

  // The status should be recomputed when the participant client list changes.
  // There are two ways we do this, 1) reacting to changes in the db due to normal
  // client discovery, and 2) explicitly syncing with the backend discover legal hold
  // devices. While 2) is ongoing, we prevent 1) from occurring, so we don't try to
  // compute the status with incomplete data.

  // To prevent update the legal hold status if we are still gathering
  // information about which clients were added/deleted.
  private val isVerifyingLegalHold = Signal(false)

  (for {
    false      <- isVerifyingLegalHold
    convIds    <- convsStorage.contents.map(_.keys)
    legalHolds <- Signal.sequence(convIds.map(convId => legalHold(convId).map(convId -> _)).toSeq: _*)
  } yield legalHolds.toMap).foreach { legalHoldMap =>
    updateConvsAndPostSystemMsgsIfNeeded(legalHoldMap)
  }

  private def updateConvsAndPostSystemMsgsIfNeeded(legalHoldMap: Map[ConvId, Boolean]): Future[Unit] = {
    for {
      updatedConvs <- convsStorage.updateAll2(legalHoldMap.keys, { conv =>
                        val detectedLegalHoldDevice = legalHoldMap(conv.id)
                        // Only update if needed.
                        if (conv.isUnderLegalHold != detectedLegalHoldDevice)
                          conv.withNewLegalHoldStatus(detectedLegalHoldDevice)
                        else
                          conv
                      })
      _            <- Future.traverse(updatedConvs) { case (prev, curr) =>
                        // Only if a change change occurred.
                        if (prev.isUnderLegalHold != curr.isUnderLegalHold)
                          appendSystemMessage(curr)
                        else
                          Future.successful(())
      }
    } yield ()
  }

  private def appendSystemMessage(conv: ConversationData, messageTime: Option[RemoteInstant] = None): Future[Unit] = {
    // We want to display the system message before the otr message
    // that gave us the legal hold hint.
    val adjustedTime = messageTime.map(_ - 5.millis)

    (if (conv.isUnderLegalHold)
      messagesService.addLegalHoldEnabledMessage(conv.id, adjustedTime)
    else
      messagesService.addLegalHoldDisabledMessage(conv.id, adjustedTime)
    ).map(_ => ())
  }

  private def clientsInConv(convId: ConvId): Signal[Set[Client]] = {
    for {
      members <- membersStorage.activeMembers(convId)
      clients <- Signal.sequence(members.map(uId => clientsStorage.optSignal(uId)).toSeq: _*)
    } yield clients.flatMap(_.fold(Set.empty[Client])(_.clients.values.toSet)).toSet
  }

  private def legalHold(convId: ConvId): Signal[Boolean] =
    clientsInConv(convId).map(_.exists(_.isLegalHoldDevice))

  override def messageEventStage: Stage.Atomic = EventScheduler.Stage[MessageEvent] ({ (_, events, tag) =>
    verbose(l"SSSTAGES<TAG:$tag> LegalHoldServiceImpl:MessageEventStage stage 1")
    Future.traverse(events) {
      case GenericMessageEvent(convId, _, time, _, _, content) =>
        updateStatusFromMessageHint(convId, content.legalHoldStatus, time)
      case _ =>
        Future.successful(())
    }
  },
    name = "LegalHoldService - MessageEvent"
  )

  private def updateStatusFromMessageHint(convId: RConvId,
                                          messageStatus: Messages.LegalHoldStatus,
                                          time: RemoteInstant): Future[Unit] = {
    (for {
      Some(conv)             <- convsStorage.getByRemoteId(convId)
      Some(update)            = statusUpdate(conv, messageStatus)
      Some((_, updatedConv)) <- convsStorage.update(conv.id, update)
      _                      <- appendSystemMessage(updatedConv, Some(time))
      _                      <- verifyLegalHold(convId)
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
    isVerifyingLegalHold ! true
    sync.syncClientsForLegalHold(convId).map(_ => ())
  }

  // This will cause the status updater signal to run again.
  override def updateLegalHoldStatusAfterFetchingClients(): Unit = isVerifyingLegalHold ! false

}

