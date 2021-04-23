package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{TeamId, UserId}
import com.waz.service.LegalHoldService
import com.waz.sync.SyncResult
import com.waz.sync.client.LegalHoldClient

import scala.concurrent.Future

trait LegalHoldSyncHandler {
  def syncLegalHoldRequest(): Future[SyncResult]
}

class LegalHoldSyncHandlerImpl(teamId: Option[TeamId],
                               userId: UserId,
                               client: LegalHoldClient,
                               service: LegalHoldService) extends LegalHoldSyncHandler with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override def syncLegalHoldRequest(): Future[SyncResult] = teamId match {
    case None =>
      Future.successful(SyncResult.Success)
    case Some(teamId) =>
      client.fetchLegalHoldRequest(teamId, userId).future.flatMap {
        case Left(error)          => Future.successful(SyncResult.Failure(error))
        case Right(None)          => service.deleteLegalHoldRequest().map(_ => SyncResult.Success)
        case Right(Some(request)) => service.storeLegalHoldRequest(request).map(_ => SyncResult.Success)
      }
  }

}

sealed trait LegalHoldError

object LegalHoldError {
  case object NotInTeam extends LegalHoldError
  case object InvalidPassword extends LegalHoldError
  case object InvalidResponse extends LegalHoldError
}
