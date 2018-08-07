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
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsUiService}
import com.waz.sync.{SyncRequestService, SyncResult, SyncServiceHandle}
import com.waz.threading.Threading
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.utils.returning

import scala.concurrent.Future

trait IntegrationsService {
  def searchIntegrations(startWith: Option[String] = None): Signal[Seq[IntegrationData]]
  def getIntegration(pId: ProviderId, iId: IntegrationId): Future[IntegrationData]
  def getProvider(id: ProviderId): Future[ProviderData]

  def onIntegrationsSynced(name: Option[String], data: Seq[IntegrationData]): Future[Unit]
  def onProviderSynced(pId: ProviderId, data: ProviderData): Future[Unit]
  def onIntegrationSynced(pId: ProviderId, iId: IntegrationId, data: IntegrationData): Future[Unit]

  def addBotToConversation(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[Either[ErrorResponse, Unit]]
  def removeBotFromConversation(cId: ConvId, botId: UserId): Future[Either[ErrorResponse, Unit]]
}

class IntegrationsServiceImpl(teamId:       Option[TeamId],
                              sync:         SyncServiceHandle,
                              syncRequests: SyncRequestService,
                              convsUi:      ConversationsUiService,
                              convs:        ConversationsContentUpdater) extends IntegrationsService {
  implicit val ctx = Threading.Background

  private var integrationSearch = Map.empty[Option[String], SourceSignal[Seq[IntegrationData]]]
  private var providers         = Map.empty[ProviderId, ProviderData]
  private var integrations      = Map.empty[IntegrationId, IntegrationData]

  override def searchIntegrations(startWith: Option[String] = None) =
    integrationSearch.getOrElse(startWith, returning(Signal[Seq[IntegrationData]]()) { sig =>
      integrationSearch += (startWith -> sig)
      sync.syncIntegrations(startWith)
    })

  override def getIntegration(pId: ProviderId, iId: IntegrationId) =
    if (integrations.contains(iId)) Future.successful(integrations(iId))
    else sync.syncIntegration(pId, iId).flatMap(syncRequests.scheduler.await).map(_ => integrations(iId))

  override def getProvider(pId: ProviderId) =
    if (providers.contains(pId)) Future.successful(providers(pId))
    else sync.syncProvider(pId).flatMap(syncRequests.scheduler.await).map(_ => providers(pId))

  override def onIntegrationsSynced(searchPrefix: Option[String], data: Seq[IntegrationData]) =
    integrationSearch.get(searchPrefix) match {
      case Some(signal) => Future.successful(signal ! data)
      case None         => Future.failed(new Exception(s"received sync data for unknown integrations search prefix: $searchPrefix"))
    }

  override def onIntegrationSynced(pId: ProviderId, iId: IntegrationId, data: IntegrationData) =
    Future.successful(integrations += iId -> data)

  override def onProviderSynced(id: ProviderId, data: ProviderData) =
    Future.successful(providers += id -> data)

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
