package com.waz.service

import com.waz.api.OtrClientType
import com.waz.api.impl.ErrorResponse
import com.waz.content.Preferences.Preference.PrefCodec.LegalHoldRequestCodec
import com.waz.content.UserPreferences
import com.waz.model.otr.ClientId
import com.waz.model.{LegalHoldRequest, LegalHoldRequestEvent, TeamId, UserId}
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
  def legalHoldRequestEventStage: Stage.Atomic
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
                           cryptoSessionService: CryptoSessionService) extends LegalHoldService {

  import com.waz.threading.Threading.Implicits.Background

  override def legalHoldRequestEventStage: Stage.Atomic = EventScheduler.Stage[LegalHoldRequestEvent] { (_, events) =>
    Future.sequence {
      events
        .filter(_.targetUserId == selfUserId)
        .map(event => storeLegalHoldRequest(event.request))
    }.map(_ => ())
  }

  private lazy val legalHoldRequestPref = userPrefs(UserPreferences.LegalHoldRequest)

  override val legalHoldRequest: Signal[Option[LegalHoldRequest]] = legalHoldRequestPref.signal

  override def getFingerprint(request: LegalHoldRequest): Option[String] =
    Try(CryptoBox.getFingerprintFromPrekey(request.lastPreKey)).toOption.map(new String(_))

  override def approveRequest(request: LegalHoldRequest,
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
    legalHoldRequestPref := Some(request)

  def deleteLegalHoldRequest(): Future[Unit] =
    legalHoldRequestPref := None

}

