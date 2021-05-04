package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{RConvId, TeamId, UserId}
import com.waz.service.LegalHoldService
import com.waz.sync.SyncResult
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.otr.OtrSyncHandler

import scala.concurrent.Future

trait LegalHoldSyncHandler {
  def syncLegalHoldRequest(): Future[SyncResult]
  def syncClientsForLegalHoldVerification(convId: RConvId): Future[SyncResult]
}

class LegalHoldSyncHandlerImpl(teamId: Option[TeamId],
                               userId: UserId,
                               client: LegalHoldClient,
                               service: LegalHoldService,
                               otrSync: OtrSyncHandler) extends LegalHoldSyncHandler with DerivedLogTag {

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

  override def syncClientsForLegalHoldVerification(convId: RConvId): Future[SyncResult] = {
    for {
      clientList <- otrSync.postClientDiscoveryMessage(convId)
      // Fetch unknown users
      // Fetch all clients
      _ <- service.updateLegalHoldStatusAfterFetchingClients(Seq())
    } yield ()
    Future.successful(SyncResult.Success)
  }

}

sealed trait LegalHoldError

object LegalHoldError {
  case object NotInTeam extends LegalHoldError
  case object InvalidPassword extends LegalHoldError
  case object InvalidResponse extends LegalHoldError
}
