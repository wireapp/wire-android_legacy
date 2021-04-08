package com.waz.service

import com.waz.api.OtrClientType
import com.waz.content.{PropertiesStorage, PropertyValue}
import com.waz.model.otr.ClientId
import com.waz.model.{LegalHoldRequest, LegalHoldRequestEvent, UserId}
import com.waz.service.EventScheduler.Stage
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
import com.waz.sync.SyncResult
import com.waz.sync.handler.{LegalHoldError, LegalHoldSyncHandler}
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.wire.signals.Signal

import scala.concurrent.Future

trait LegalHoldService {
  def legalHoldRequestEventStage: Stage.Atomic
  def syncLegalHoldRequest(): Future[SyncResult]
  def legalHoldRequest: Signal[Option[LegalHoldRequest]]
  def approveRequest(request: LegalHoldRequest,
                     password: Option[String]): Future[Either[LegalHoldError, Unit]]
}

class LegalHoldServiceImpl(selfUserId: UserId,
                           storage: PropertiesStorage,
                           syncHandler: LegalHoldSyncHandler,
                           clientsService: OtrClientsService,
                           cryptoSessionService: CryptoSessionService) extends LegalHoldService {

  import LegalHoldService._
  import com.waz.threading.Threading.Implicits.Background

  override def legalHoldRequestEventStage: Stage.Atomic = EventScheduler.Stage[LegalHoldRequestEvent] { (_, events) =>
    Future.sequence {
      events
        .filter(_.targetUserId == selfUserId)
        .map(event => storeRequest(event.request))
    }.map(_ => ())
  }

  override def syncLegalHoldRequest(): Future[SyncResult] = syncHandler.fetchLegalHoldRequest().flatMap {
    case Right(Some(request)) => storeRequest(request).map(_ => SyncResult.Success)
    case Right(None)          => deleteRequest().map(_ => SyncResult.Success)
    case Left(err)            => Future.successful(SyncResult.Failure(err))
  }

  override def legalHoldRequest: Signal[Option[LegalHoldRequest]] = {
    storage.optSignal(LegalHoldRequestKey).map { property =>
      property.map(_.value).map(JsonDecoder.decode[LegalHoldRequest])
    }
  }

  override def approveRequest(request: LegalHoldRequest, password: Option[String]): Future[Either[LegalHoldError, Unit]] = for {
    _      <- createLegalHoldClientAndSession(request)
    result <- syncHandler.approveRequest(password)
    _      <- if (result.isLeft) {
                deleteLegalHoldClientAndSession(request.clientId)
              } else {
                Future.successful({})
              }
  } yield { result }

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

  private def storeRequest(request: LegalHoldRequest): Future[Unit] = {
    val value = JsonEncoder.encode[LegalHoldRequest](request).toString
    storage.save(PropertyValue(LegalHoldRequestKey, value))
  }

  private def deleteRequest(): Future[Unit] =
    storage.deleteByKey(LegalHoldRequestKey)

}

object LegalHoldService {

  val LegalHoldRequestKey: PropertyKey = PropertyKey("legal-hold-request")

}
