package com.waz.zclient.legalhold

import com.waz.model.{ConvId, LegalHoldRequest, UserId}
import com.waz.service.LegalHoldService
import com.waz.sync.handler.LegalHoldError
import com.waz.zclient.{Injectable, Injector}
import com.wire.signals.Signal

import scala.concurrent.Future

//TODO: implement status calculation
class LegalHoldController(implicit injector: Injector)
  extends Injectable {

  import com.waz.threading.Threading.Implicits.Background

  private lazy val legalHoldService = inject[Signal[LegalHoldService]]

  def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    Signal.const(false)

  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    Signal.const(false)

  def legalHoldRequest: Signal[Option[LegalHoldRequest]] =
    legalHoldService.flatMap(_.legalHoldRequest)

  def approveRequest(password: Option[String]): Future[Either[LegalHoldError, Unit]] = for {
    service <- legalHoldService.head
    request <- service.legalHoldRequest.head
    result  <- request match {
      case None    => Future.successful(Right({}))
      case Some(r) => service.approveRequest(r, password)
    }
  } yield result

}
