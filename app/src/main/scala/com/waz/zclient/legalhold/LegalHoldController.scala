package com.waz.zclient.legalhold

import com.waz.model.AccountData.Password
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage}
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
  private lazy val convsStorage = inject[Signal[ConversationStorage]]
  private lazy val clientsStorage = inject[Signal[OtrClientsStorage]]
  private lazy val memberStorage = inject[Signal[MembersStorage]]

  val onLegalHoldSubjectClick: SourceStream[UserId] = EventStream[UserId]

  def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    clientsStorage.flatMap {
      _.optSignal(userId).map(_.fold(false)(_.clients.values.exists(_.isLegalHoldDevice)))
    }

  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    convsStorage.flatMap {
      _.optSignal(conversationId).map(_.fold(false)(_.isUnderLegalHold))
    }

  def legalHoldUsers(conversationId: ConvId): Signal[Seq[UserId]] = for {
    members           <- memberStorage
    users             <- members.activeMembers(conversationId)
    usersAndStatus    <- Signal.sequence(users.map(userId => Signal.zip(Signal.const(userId), isLegalHoldActive(userId))).toSeq: _*)
    legalHoldSubjects = usersAndStatus.filter(_._2).map(_._1)
  } yield legalHoldSubjects

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
