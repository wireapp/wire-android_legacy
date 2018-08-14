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

import com.waz.content.AssetsStorage
import com.waz.model._
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.IntegrationsClient
import com.waz.sync.queue.SyncScheduler
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.threading.{CancellableFuture, Threading}

import scala.concurrent.Future

class IntegrationsServiceSpec extends AndroidFreeSpec {

  implicit val ctx = Threading.Background

  val sync = mock[SyncServiceHandle]
  val client = mock[IntegrationsClient]

  val syncScheduler = mock[SyncScheduler]
  val srs = mock[SyncRequestService]
  val assets = mock[AssetsStorage]

  lazy val service = new IntegrationsServiceImpl(Some(TeamId()), client, assets, sync, srs)


  feature("assets") {

    scenario("Previously downloaded AssetData are not recreated if found in database") {

      val asset1 = AssetData(id = AssetId("asset-1"), remoteId = Some(RAssetId("asset-1")))
      val asset1Copy = AssetData(id = AssetId("asset-1-copy"), remoteId = Some(RAssetId("asset-1")))
      val asset2 = AssetData(id = AssetId("asset-2"), remoteId = Some(RAssetId("asset-2")))

      val service1 = IntegrationData(id = IntegrationId("service-1"), provider = ProviderId(""), asset = Some(asset1Copy.id))
      val service2 = IntegrationData(id = IntegrationId("service-2"), provider = ProviderId(""), asset = Some(asset2.id))

      val beResponse = Map(
        service1 -> Some(asset1Copy), //get some integration a second time from the backend
        service2 -> Some(asset2)
      )

      val fromDatabase = Set(asset1)

      (client.searchTeamIntegrations _).expects(None, *).returning(CancellableFuture.successful(Right(beResponse)))

      (assets.findByRemoteIds _).expects(Set(asset1Copy.remoteId.get, asset2.remoteId.get)).returning(Future.successful(fromDatabase))
      (assets.insertAll _).expects(Set(asset2)).returning(Future.successful(Set()))

      val res = result(service.searchIntegrations())

      res.right.get.find(_.id == service1.id).get shouldEqual service1.copy(asset = Some(asset1.id))
      res.right.get.find(_.id == service2.id).get shouldEqual service2.copy(asset = Some(asset2.id))


    }
  }

}