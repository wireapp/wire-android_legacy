package com.waz.zclient.legalhold

import com.waz.model.AccountData.Password
import com.waz.model.{ConvId, LegalHoldRequest, UserId}
import com.waz.service.LegalHoldService
import com.waz.sync.handler.LegalHoldError
import com.waz.zclient.{Injectable, Injector}
import com.wire.signals.{EventStream, Signal, SourceStream}

import scala.concurrent.Future

//TODO: implement status calculation
class LegalHoldController(implicit injector: Injector)
  extends Injectable {

  import com.waz.threading.Threading.Implicits.Background

  private lazy val legalHoldService = inject[Signal[LegalHoldService]]

  val onLegalHoldSubjectClick: SourceStream[UserId] = EventStream[UserId]

  def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    Signal.const(false)

  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    Signal.const(false)

  def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]] =
    Signal.const(Seq.empty)

  def legalHoldRequest: Signal[Option[LegalHoldRequest]] =
    legalHoldService.flatMap(_.legalHoldRequest)

  def approveRequest(password: Option[Password]): Future[Either[LegalHoldError, Unit]] = for {
    service <- legalHoldService.head
    request <- service.legalHoldRequest.head
    result  <- request match {
      case None    => Future.successful(Right({}))
      case Some(r) => service.approveRequest(r, password.map(_.str))
    }
  } yield result

}
