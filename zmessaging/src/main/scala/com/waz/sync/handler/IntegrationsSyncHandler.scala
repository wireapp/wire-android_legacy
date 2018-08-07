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
package com.waz.sync.handler

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.impl.ErrorResponse
import com.waz.model._
import com.waz.service.assets.AssetService
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.{EventPipeline, IntegrationsService}
import com.waz.sync.SyncResult
import com.waz.sync.client.IntegrationsClient
import com.waz.threading.Threading

import scala.concurrent.Future

trait IntegrationsSyncHandler {
  def syncProvider(pId: ProviderId): Future[SyncResult]
  def syncIntegrations(name: Option[String]): Future[SyncResult]
  def syncIntegration(pId: ProviderId, iId: IntegrationId): Future[SyncResult]

  def addBot(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[SyncResult]
  def removeBot(cId: ConvId, botId: UserId): Future[SyncResult]
}

class IntegrationsSyncHandlerImpl(selfUserId: UserId,
                                  teamId:     Option[TeamId],
                                  convs:      ConversationsContentUpdater,
                                  assets:     AssetService,
                                  client:     IntegrationsClient,
                                  service:    IntegrationsService,
                                  pipeline:   EventPipeline) extends IntegrationsSyncHandler {
  import Threading.Implicits.Background

  override def syncProvider(pId: ProviderId) =
    client.getProvider(pId).future.flatMap {
      case Right(data) =>
        verbose(s"querying for provider with id $pId returned $data")
        service.onProviderSynced(pId, data).map(_ => SyncResult.Success)
      case Left(error) =>
        verbose(s"querying for provider with id $pId returned $error")
        Future.successful(SyncResult(error))
    }

  override def syncIntegration(pId: ProviderId, iId: IntegrationId) =
    client.getIntegration(pId, iId).future.flatMap {
      case Right((integ, asset)) =>
        verbose(s"querying for integration with pId $pId and iId $iId returned $integ with asset: $asset")
        assets.updateAssets(asset.toSeq)
        service.onIntegrationSynced(pId, iId, integ).map(_ => SyncResult.Success)
      case Left(error) =>
        verbose(s"querying for provider with pId $pId and iId $iId returned $error")
        Future.successful(SyncResult(error))
    }

  override def syncIntegrations(name: Option[String]) = teamId match {
    case Some(tId) =>
      client.searchTeamIntegrations(name, tId).future.flatMap {
        case Right(integs) =>
          verbose(s"querying for integrations with name $name returned $integs")
          assets.updateAssets(integs.values.flatten.toSeq)
          service.onIntegrationsSynced(name, integs.keys.toSeq).map(_ => SyncResult.Success)
        case Left(error) =>
          verbose(s"querying for integrations with name $name returned $error")
          Future.successful(SyncResult(error))
      }

    case _ =>
      Future.successful(SyncResult.Failure(Some(ErrorResponse.internalError("Services are not available on personal accounts")), shouldRetry = false))

  }


  override def addBot(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[SyncResult] =
    convs.convById(cId).collect { case Some(c) => c.remoteId }.flatMap { rId =>
      client.addBot(rId, pId, iId).future.flatMap {
        case Right(event) =>
          verbose(s"addBot($cId, $pId, $iId)")
          pipeline(Seq(event)).map(_ => SyncResult.Success)
        case Left(resp@ErrorResponse(502, _, "bad-gateway")) =>
          verbose(s"bot refuses to be added $resp")
          Future.successful(SyncResult.Failure(Some(resp), shouldRetry = false))
        case Left(resp@ErrorResponse(409, _, "too-many-bots")) =>
          verbose(s"too many bots in a conversation $resp")
          Future.successful(SyncResult.Failure(Some(resp), shouldRetry = false))
        case Left(error) =>
          verbose(s"addBot returned $error")
          Future.successful(SyncResult(error))
      }
    }

  override def removeBot(cId: ConvId, userId: UserId): Future[SyncResult] =
    convs.convById(cId).collect { case Some(c) => c.remoteId }.flatMap { rId =>
      client.removeBot(rId, userId).future.flatMap {
        case Right(event) =>
          verbose(s"removeBot($cId, $userId)")
          pipeline(Seq(event)).map(_ => SyncResult.Success)
        case Left(error) =>
          verbose(s"removeBot returned $error")
          Future.successful(SyncResult(error))
      }
    }
}
