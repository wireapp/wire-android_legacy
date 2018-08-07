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

import com.waz.model._
import com.waz.service.assets.AssetService
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsUiService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.IntegrationsClient
import com.waz.sync.handler.IntegrationsSyncHandlerImpl
import com.waz.sync.queue.SyncScheduler
import com.waz.sync.{SyncRequestService, SyncResult, SyncServiceHandle}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.wrappers.URI

import scala.concurrent.{Future, Promise}

class IntegrationsServiceSpec extends AndroidFreeSpec {
  import IntegrationsServiceSpec._

  implicit val ctx = Threading.Background

  val sync = mock[SyncServiceHandle]
  val client = mock[IntegrationsClient]

  private var syncMap = Map[SyncId, Promise[SyncResult]]()

  val syncScheduler = mock[SyncScheduler]
  val srs           = mock[SyncRequestService]
  val convsUi       = mock[ConversationsUiService]
  val convs         = mock[ConversationsContentUpdater]
  val pipeline      = mock[EventPipeline]
  val errors        = mock[ErrorsService]
  val assets        = mock[AssetService]

  (syncScheduler.await(_: SyncId)).expects(*).anyNumberOfTimes().onCall( (sid: SyncId) => { syncMap(sid).future } )
  (srs.scheduler _).expects().anyNumberOfTimes().returning(syncScheduler)
  (assets.updateAssets _).expects(*).anyNumberOfTimes().onCall((as: Seq[AssetData]) => Future.successful(as.toSet))

  val service: IntegrationsService = new IntegrationsServiceImpl(Some(TeamId()), sync, srs, convsUi, convs)
  val handler = new IntegrationsSyncHandlerImpl(UserId(), Some(TeamId()), convs, assets, client, service, pipeline)

  (sync.syncProvider _).expects(*).anyNumberOfTimes().onCall((id: ProviderId) => Future {
    val sid = SyncId()
    syncMap += (sid -> Promise[SyncResult]())
    handler.syncProvider(id).map { _ => syncMap(sid).success(SyncResult.Success) }
    sid
  })

  (sync.syncIntegrations _).expects(*).anyNumberOfTimes().onCall((startWith: Option[String]) => Future {
    handler.syncIntegrations(startWith)
    SyncId()
  })

  (client.getProvider _).expects(*).anyNumberOfTimes().onCall((id: ProviderId) => providers.get(id) match {
    case Some(data) => CancellableFuture.successful(Right(data))
    case None       => CancellableFuture.failed(new Exception(s"no provider with id $id"))
  })

  (client.searchTeamIntegrations _).expects(*, *).anyNumberOfTimes().onCall { (startWith: Option[String], teamId: TeamId) =>
    val integs = integrations.values.filter(_.name.startsWith(startWith.getOrElse(""))).map(_ -> Option.empty[AssetData]).toMap
    CancellableFuture.successful(Right(integs))
  }

  feature("integrations") {
    scenario("get all integrations") {
      val integrations = service.searchIntegrations()
      result(integrations.head).size shouldEqual 3
    }

    scenario("get all integrations starting with 'Ech'") {
      val integrations = service.searchIntegrations(Some("Ech"))
      result(integrations.head).size shouldEqual 2
    }
  }

  feature("providers") {
    scenario("get Wire provider") {
      result(service.getProvider(provider0.id)) shouldEqual provider0
    }
  }
  
}

object IntegrationsServiceSpec {
  val provider0 = ProviderData(
    ProviderId("19c092cb-27cf-42f9-812e-a10de4f2dcae"),
    "Wire Swiss GmbH",
    EmailAddress("support@wire.com"),
    URI.parse("https://wire.com/"),
    "The Wire Team"
  )

  val provider1 = ProviderData(
    ProviderId("5671b6be-f958-4b85-aecc-a5fcffa856fa"),
    "Collections Corp",
    EmailAddress("support@coco.com"),
    URI.parse("https://coco.com/"),
    "The Collections Corp providing collectionsbot"
  )

  val provider2 = ProviderData(
    ProviderId("6ca9ae2b-b6ac-4e9b-a868-d648d51787a3"),
    "Echo Company",
    EmailAddress("support@echo.com"),
    URI.parse("https://echo.com/"),
    "Echo Company providing high-quality echo"
  )

  val provider3 = ProviderData(
    ProviderId("fd6ffe9f-d943-43d7-a691-9bb9e4d1b964"),
    "blah blah",
    EmailAddress("support@blah.com"),
    URI.parse("https://blah.com/"),
    "blah blah blah"
  )

  val providers = List(provider0, provider1, provider2, provider3).map(p => p.id -> p).toMap

  val integration1 = IntegrationData(
    IntegrationId("07653181-7c72-4a3a-8e76-39fcbf27fd17"),
    provider1.id,
    "collectionsbot",
    "collections bot short description",
    "Helps you fill your library",
    None,
    Seq("tutorial"),
    enabled = true
  )

  val integration2 = IntegrationData(
    IntegrationId("748bda63-7783-42d4-80c2-030e3daef5c7"),
    provider2.id,
    "Echo",
    "echo",
    "Echo",
    None,
    Seq("tutorial"),
    enabled = true
  )

  val integration3 = IntegrationData(
    IntegrationId("f21ef724-64cc-45e3-b78d-d1b18a7c02a5"),
    provider3.id,
    "Echo_stage",
    "echo",
    "blah",
    None,
    Seq("tutorial"),
    enabled = true
  )

  val integrations = List(integration1, integration2, integration3).map(i => i.id -> i).toMap
}