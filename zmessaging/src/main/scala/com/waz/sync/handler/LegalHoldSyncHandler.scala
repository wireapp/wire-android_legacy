package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{LegalHoldRequest, TeamId, UserId}
import com.waz.sync.client.LegalHoldClient

import scala.concurrent.Future

trait LegalHoldSyncHandler {
  def fetchLegalHoldRequest(): Future[Either[ErrorResponse, Option[LegalHoldRequest]]]
}

class LegalHoldSyncHandlerImpl(teamId: Option[TeamId], userId: UserId, client: LegalHoldClient)
  extends LegalHoldSyncHandler with DerivedLogTag {

  override def fetchLegalHoldRequest(): Future[Either[ErrorResponse, Option[LegalHoldRequest]]] = teamId match {
    case None         => Future.successful(Right(None))
    case Some(teamId) => client.fetchLegalHoldRequest(teamId, userId).future
  }

}
