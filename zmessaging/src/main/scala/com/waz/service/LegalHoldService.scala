package com.waz.service

import com.waz.api.OtrClientType
import com.waz.api.impl.ErrorResponse
import com.waz.content.{PropertiesStorage, PropertyValue}
import com.waz.model.otr.ClientId
import com.waz.model.{LegalHoldRequest, LegalHoldRequestEvent, TeamId, UserId}
import com.waz.service.EventScheduler.Stage
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.handler.LegalHoldError
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.wire.signals.Signal

import scala.concurrent.Future

trait LegalHoldService {
  def legalHoldRequestEventStage: Stage.Atomic
  def legalHoldRequest: Signal[Option[LegalHoldRequest]]
  def deleteLegalHoldRequest(): Future[Unit]
  def storeLegalHoldRequest(request: LegalHoldRequest): Future[Unit]
  def approveRequest(request: LegalHoldRequest,
                     password: Option[String]): Future[Either[LegalHoldError, Unit]]
}

class LegalHoldServiceImpl(selfUserId: UserId,
                           teamId: Option[TeamId],
                           storage: PropertiesStorage,
                           client: LegalHoldClient,
                           clientsService: OtrClientsService,
                           cryptoSessionService: CryptoSessionService) extends LegalHoldService {

  import LegalHoldService._
  import com.waz.threading.Threading.Implicits.Background

  override def legalHoldRequestEventStage: Stage.Atomic = EventScheduler.Stage[LegalHoldRequestEvent] { (_, events) =>
    Future.sequence {
      events
        .filter(_.targetUserId == selfUserId)
        .map(event => storeLegalHoldRequest(event.request))
    }.map(_ => ())
  }

  override def legalHoldRequest: Signal[Option[LegalHoldRequest]] = {
    storage.optSignal(LegalHoldRequestKey).map { property =>
      property.map(_.value).map(JsonDecoder.decode[LegalHoldRequest])
    }
  }

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

  def storeLegalHoldRequest(request: LegalHoldRequest): Future[Unit] = {
    val value = JsonEncoder.encode[LegalHoldRequest](request).toString
    storage.save(PropertyValue(LegalHoldRequestKey, value))
  }

  def deleteLegalHoldRequest(): Future[Unit] =
    storage.deleteByKey(LegalHoldRequestKey)

}

object LegalHoldService {

  val LegalHoldRequestKey: PropertyKey = PropertyKey("legal-hold-request")

}
