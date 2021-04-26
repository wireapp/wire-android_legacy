package com.waz.service

import com.waz.api.OtrClientType
import com.waz.api.impl.ErrorResponse
import com.waz.content.Preferences.Preference.PrefCodec.LegalHoldRequestCodec
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage, UserPreferences}
import com.waz.model.otr.ClientId
import com.waz.model.{ConvId, LegalHoldRequest, LegalHoldRequestEvent, TeamId, UserId}
import com.waz.service.EventScheduler.Stage
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.handler.LegalHoldError
import com.wire.signals.Signal

import scala.concurrent.Future

trait LegalHoldService {
  def legalHoldRequestEventStage: Stage.Atomic
  def isLegalHoldActive(userId: UserId): Signal[Boolean]
  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean]
  def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]]
  def legalHoldRequest: Signal[Option[LegalHoldRequest]]
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

  def legalHoldRequestEventStage: Stage.Atomic = EventScheduler.Stage[LegalHoldRequestEvent] { (_, events) =>
    Future.sequence {
      events
        .filter(_.targetUserId == selfUserId)
        .map(event => storeLegalHoldRequest(event.request))
    }.map(_ => ())
  }

  def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    clientsStorage.optSignal(userId).map(_.fold(false)(_.clients.values.exists(_.isLegalHoldDevice)))

  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    convsStorage.optSignal(conversationId).map(_.fold(false)(_.isUnderLegalHold))

  def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]] = for {
    users             <- membersStorage.activeMembers(conversationId)
    usersAndStatus    <- Signal.sequence(users.map(userId => Signal.zip(Signal.const(userId), isLegalHoldActive(userId))).toSeq: _*)
    legalHoldSubjects = usersAndStatus.filter(_._2).map(_._1)
  } yield legalHoldSubjects

  def legalHoldRequest: Signal[Option[LegalHoldRequest]] =
    userPrefs.preference(UserPreferences.LegalHoldRequest).signal

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
    legalHoldClient = client.copy(devType = OtrClientType.LEGALHOLD)
    _               <- clientsService.updateUserClients(selfUserId, Seq(legalHoldClient))
    sessionId       = SessionId(selfUserId, legalHoldClient.id)
    _               <- cryptoSessionService.getOrCreateSession(sessionId, request.lastPreKey)
  } yield ()

  private def deleteLegalHoldClientAndSession(clientId: ClientId): Future[Unit] = for {
    _ <- clientsService.removeClients(selfUserId, Seq(clientId))
    _ <- cryptoSessionService.deleteSession(SessionId(selfUserId, clientId))
  } yield ()

  def storeLegalHoldRequest(request: LegalHoldRequest): Future[Unit] =
    userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(request))

  def deleteLegalHoldRequest(): Future[Unit] =
    userPrefs.setValue(UserPreferences.LegalHoldRequest, None)

}

