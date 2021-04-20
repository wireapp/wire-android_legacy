package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{LegalHoldRequest, TeamId, UserId}
import com.waz.sync.client.LegalHoldClient

import scala.concurrent.Future

trait LegalHoldSyncHandler {
  def fetchLegalHoldRequest(): Future[Either[ErrorResponse, Option[LegalHoldRequest]]]
  def approveRequest(password: Option[String]): Future[Either[LegalHoldError, Unit]]
}

class LegalHoldSyncHandlerImpl(teamId: Option[TeamId], userId: UserId, client: LegalHoldClient)
  extends LegalHoldSyncHandler with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override def fetchLegalHoldRequest(): Future[Either[ErrorResponse, Option[LegalHoldRequest]]] = teamId match {
    case None         => Future.successful(Right(None))
    case Some(teamId) => client.fetchLegalHoldRequest(teamId, userId).future
  }

  override def approveRequest(password: Option[String]): Future[Either[LegalHoldError, Unit]] = teamId match {
    case None =>
      Future.successful(Left(LegalHoldError.NotInTeam))
    case Some(teamId) =>
      client.approveRequest(teamId, userId, password).future.map {
        case Left(ErrorResponse(_, _, label)) if label == "access-denied" || label == "invalid-payload" =>
          Left(LegalHoldError.InvalidPassword)
        case Left(_) =>
          Left(LegalHoldError.InvalidResponse)
        case Right(_) =>
          Right(())
      }
  }

}

sealed trait LegalHoldError

object LegalHoldError {
  case object NotInTeam extends LegalHoldError
  case object InvalidPassword extends LegalHoldError
  case object InvalidResponse extends LegalHoldError
}
