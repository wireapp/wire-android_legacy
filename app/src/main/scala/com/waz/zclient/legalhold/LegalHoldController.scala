package com.waz.zclient.legalhold

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.log.LogUI._
import com.waz.model.AccountData.Password
import com.waz.model.{ConvId, LegalHoldRequest, UserId}
import com.waz.service.LegalHoldService
import com.waz.sync.handler.LegalHoldError
import com.waz.zclient.{Injectable, Injector}
import com.wire.signals.{EventStream, Signal, SourceStream}

import scala.concurrent.Future

//TODO: implement status calculation
class LegalHoldController(implicit injector: Injector)
  extends Injectable with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  private lazy val legalHoldService = inject[Signal[LegalHoldService]]

  val onLegalHoldSubjectClick: SourceStream[UserId] = EventStream[UserId]

  def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    Signal.const(false)

  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    Signal.const(false)

  def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]] =
    Signal.const(Seq.empty)

  val legalHoldRequest: Signal[Option[LegalHoldRequest]] =
    for {
      service <- legalHoldService
      request <- service.legalHoldRequest
      _        = verbose(l"legalHoldRequest changed: $r")
    } yield request

  val hasPendingRequest: Signal[Boolean] = legalHoldRequest.map(_.isDefined)

  def getFingerprint(request: LegalHoldRequest): Future[Option[String]] =
    legalHoldService.head.map(_.getFingerprint(request))

  def approveRequest(password: Option[Password]): Future[Either[LegalHoldError, Unit]] = for {
    service <- legalHoldService.head
    request <- service.legalHoldRequest.head
    result  <- request match {
      case None    => Future.successful(Right({}))
      case Some(r) => service.approveRequest(r, password.map(_.str))
    }
  } yield result

}
