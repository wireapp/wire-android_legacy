package com.waz.sync.handler

import com.waz.content.{OtrClientsStorage, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{RConvId, TeamId, UserId}
import com.waz.service.{LegalHoldService, UserService}
import com.waz.sync.{SyncRequestService, SyncResult}
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.otr.OtrSyncHandler

import scala.concurrent.Future

trait LegalHoldSyncHandler {
  def syncLegalHoldRequest(): Future[SyncResult]
  def syncClientsForLegalHoldVerification(convId: RConvId): Future[SyncResult]
}

class LegalHoldSyncHandlerImpl(teamId: Option[TeamId],
                               userId: UserId,
                               apiClient: LegalHoldClient,
                               legalHoldService: LegalHoldService,
                               userService: UserService,
                               clientsStorage: OtrClientsStorage,
                               otrSync: OtrSyncHandler,
                               syncRequestService: SyncRequestService) extends LegalHoldSyncHandler with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override def syncLegalHoldRequest(): Future[SyncResult] = teamId match {
    case None =>
      Future.successful(SyncResult.Success)
    case Some(teamId) =>
      apiClient.fetchLegalHoldRequest(teamId, userId).future.flatMap {
        case Left(error)    => Future.successful(SyncResult.Failure(error))
        case Right(request) => legalHoldService.onLegalHoldRequestSynced(request).map(_ => SyncResult.Success)
      }
  }

  override def syncClientsForLegalHoldVerification(convId: RConvId): Future[SyncResult] = {
    otrSync.postClientDiscoveryMessage(convId).flatMap {
      case Left(errorResponse) =>
        Future.successful(SyncResult.Failure(errorResponse))
      case Right(clientsMap) =>
        val userIds = clientsMap.userIds

        for {
          id1    <- userService.syncIfNeeded(userIds)
          id2    <- userService.syncClients(userIds)
          allIds =  Set(id1, Some(id2)).collect { case Some(id) => id }
          _      <- syncRequestService.await(allIds)
        } yield {
          SyncResult.Success
        }
    }.map { result =>
      legalHoldService.updateLegalHoldStatusAfterFetchingClients()
      result
    }
  }

}

sealed trait LegalHoldError

object LegalHoldError {
  case object NotInTeam extends LegalHoldError
  case object InvalidPassword extends LegalHoldError
  case object InvalidResponse extends LegalHoldError
}
