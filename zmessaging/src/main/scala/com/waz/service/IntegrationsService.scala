/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.model._
import com.waz.service.assets.AssetService
import com.waz.sync.client.{ErrorOr, IntegrationsClient}
import com.waz.sync.{SyncRequestService, SyncResult, SyncServiceHandle}
import com.waz.threading.Threading

import scala.concurrent.Future

trait IntegrationsService {
  def searchIntegrations(startWith: Option[String] = None): ErrorOr[Seq[IntegrationData]]
  def getIntegration(pId: ProviderId, iId: IntegrationId): ErrorOr[IntegrationData]

  def addBotToConversation(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[Either[ErrorResponse, Unit]]
  def removeBotFromConversation(cId: ConvId, botId: UserId): Future[Either[ErrorResponse, Unit]]
}

class IntegrationsServiceImpl(teamId:       Option[TeamId],
                              client:       IntegrationsClient,
                              assets:       AssetService,
                              sync:         SyncServiceHandle,
                              syncRequests: SyncRequestService) extends IntegrationsService {
  implicit val ctx = Threading.Background

  override def searchIntegrations(startWith: Option[String] = None) =
    teamId match {
      case Some(tId) => client.searchTeamIntegrations(startWith, tId).future.flatMap {
        case Right(integrations) =>
          assets
            .updateAssets(integrations.values.flatten.toSeq)
            .map(_ => Right(integrations.keys.toSeq))
        case Left(err) => Future.successful(Left(err))
      }
      case None => Future.successful(Right(Seq.empty[IntegrationData]))
    }

  override def getIntegration(pId: ProviderId, iId: IntegrationId) =
    client.getIntegration(pId, iId).future.flatMap {
      case Right((integration, asset)) =>
        assets.updateAssets(asset.toSeq).map(_ => Right(integration))
      case Left(err) => Future.successful(Left(err))
    }

  // pId here is redundant - we can take it from our 'integrations' map
  override def addBotToConversation(cId: ConvId, pId: ProviderId, iId: IntegrationId) =
    (for {
      syncId <- sync.postAddBot(cId, pId, iId)
      result <- syncRequests.scheduler.await(syncId)
    } yield result).map {
      case SyncResult.Success => Right({})
      case SyncResult.Failure(Some(error), _) => Left(error)
      case _ => Left(internalError("Unknown error"))
    }

  override def removeBotFromConversation(cId: ConvId, botId: UserId) =
    (for {
      syncId <- sync.postRemoveBot(cId, botId)
      result <- syncRequests.scheduler.await(syncId)
    } yield result).map {
      case SyncResult.Success => Right({})
      case SyncResult.Failure(Some(error), _) => Left(error)
      case _ => Left(internalError("Unknown error"))
    }
}
