package com.waz.service

import com.waz.api.impl.ErrorResponse
import com.waz.content.Preferences.Preference.PrefCodec.LegalHoldRequestCodec
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage, UserPreferences}
import com.waz.model.ConversationData.LegalHoldStatus
import com.waz.model.otr.ClientId
import com.waz.model._
import com.waz.model.otr.Client.DeviceClass
import com.waz.service.EventScheduler.Stage
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
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
}

class LegalHoldServiceImpl(selfUserId: UserId,
                           teamId: Option[TeamId],
                           userPrefs: UserPreferences,
                           client: LegalHoldClient,
                           clientsService: OtrClientsService,
                           clientsStorage: OtrClientsStorage,
                           convsStorage: ConversationStorage,
                           membersStorage: MembersStorage,
                           cryptoSessionService: CryptoSessionService) extends LegalHoldService {

  import com.waz.threading.Threading.Implicits.Background

  override def legalHoldEventStage: Stage.Atomic = EventScheduler.Stage[LegalHoldEvent] { (_, events) =>
    Future.traverse(events)(processEvent)
  }

  private def processEvent(event: LegalHoldEvent): Future[Unit] = event match {
    case LegalHoldRequestEvent(userId, request) if userId == selfUserId =>
      storeLegalHoldRequest(request)

    case LegalHoldEnableEvent(userId) if userId == selfUserId =>
      onLegalHoldApprovedFromAnotherDevice()

    case LegalHoldDisableEvent(userId) if userId == selfUserId =>
      onLegalHoldDisabled()

    case _ =>
      Future.successful({})
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
  private lazy val legalHoldDisclosurePref = userPrefs(UserPreferences.LegalHoldDisclosureType)

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

  private def onLegalHoldApprovedFromAnotherDevice(): Future[Unit] = for {
    _ <- deleteLegalHoldRequest()
    _ <- legalHoldDisclosurePref := Some(LegalHoldStatus.Enabled)
  } yield()

  private def onLegalHoldDisabled(): Future[Unit] = for {
    _ <- deleteLegalHoldRequest()
    _ <- legalHoldDisclosurePref := Some(LegalHoldStatus.Disabled)
  } yield()

  def storeLegalHoldRequest(request: LegalHoldRequest): Future[Unit] =
    legalHoldRequestPref := Some(request)

  def deleteLegalHoldRequest(): Future[Unit] =
    legalHoldRequestPref := None

}

