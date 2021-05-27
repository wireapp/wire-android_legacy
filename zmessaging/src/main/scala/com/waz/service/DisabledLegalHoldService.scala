package com.waz.service
import com.waz.model.{ConvId, Event, LegalHoldRequest, UserId}
import com.waz.service.EventScheduler.Stage
import com.waz.sync.handler.LegalHoldError
import com.wire.signals.Signal

import scala.concurrent.Future
import scala.concurrent.Future.successful

/**
 * No-op dummy LegalHoldService implementation that is used when Legal hold feature flag is off.
 */
class DisabledLegalHoldService extends LegalHoldService {

  override def legalHoldEventStage: Stage.Atomic = EventScheduler.Stage[Event]((_, _) => successful(()))

  override def messageEventStage: Stage.Atomic = EventScheduler.Stage[Event]((_, _) => successful(()))

  override def isLegalHoldActiveForSelfUser(): Signal[Boolean] = Signal.const(false)

  override def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] = Signal.const(false)

  override def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]] = Signal.const(Seq())

  override def legalHoldRequest: Signal[Option[LegalHoldRequest]] = Signal.const(Option.empty)

  override def getFingerprint(request: LegalHoldRequest): Option[String] = Option.empty

  override def approveRequest(request: LegalHoldRequest, password: Option[String]): Future[Either[LegalHoldError, Unit]] =
    Future.successful(Right(()))

  override def onLegalHoldRequestSynced(request: Option[LegalHoldRequest]): Future[Unit] = Future.successful(())

  override def updateLegalHoldStatusAfterFetchingClients(): Unit = Future.successful(())
}
