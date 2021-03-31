package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{LegalHoldRequest, TeamId, UserId}
import com.waz.sync.client.LegalHoldClient

import scala.concurrent.Future

trait LegalHoldSyncHandler {
  def fetchLegalHoldRequest(): Future[Option[LegalHoldRequest]]
}

class LegalHoldSyncHandlerImpl(teamId: Option[TeamId], userId: UserId, client: LegalHoldClient)
  extends LegalHoldSyncHandler with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override def fetchLegalHoldRequest(): Future[Option[LegalHoldRequest]] = teamId match {
    case None =>
      Future.successful(None)
    case Some(teamId) =>
      client.fetchLegalHoldRequest(teamId, userId).future.map {
        case Left(err) =>
          error(l"Unable to fetch legal hold request: $err")
          None
        case Right(request) =>
          request
    }
  }

}
