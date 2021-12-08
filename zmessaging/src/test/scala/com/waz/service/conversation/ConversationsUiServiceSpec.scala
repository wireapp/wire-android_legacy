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
package com.waz.service.conversation

import com.waz.content._
import com.waz.model._
import com.waz.service.assets.{AssetService, UriHelper}
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.PushService
import com.waz.service.{ErrorsService, NetworkModeService, PropertiesService, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}
import com.waz.zms.BuildConfig

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ConversationsUiServiceSpec extends AndroidFreeSpec {

  val selfUserId = UserId()
  val domain = Domain("chala.wire.link")
  val push =            mock[PushService]
  val users =           mock[UserService]
  val convsStorage =    mock[ConversationStorage]
  val content =         mock[ConversationsContentUpdater]
  val convsService =    mock[ConversationsService]
  val sync =            mock[SyncServiceHandle]
  val requests =        mock[SyncRequestService]
  val errors =          mock[ErrorsService]
  val uriHelper =       mock[UriHelper]
  val messages =        mock[MessagesService]
  val client =          mock[ConversationsClient]
  val messagesStorage = mock[MessagesStorage]
  val deletions =       mock[MsgDeletionStorage]
  val members =         mock[MembersStorage]
  val assetService =    mock[AssetService]
  val network =         mock[NetworkModeService]
  val properties =      mock[PropertiesService]
  val buttons =         mock[ButtonsStorage]

  val prefs     = new TestGlobalPreferences()
  val userPrefs = new TestUserPreferences()
  private def getService(teamId: Option[TeamId] = None): ConversationsUiService = {
    val msgContent = new MessagesContentUpdater(messagesStorage, convsStorage, deletions, buttons, prefs, userPrefs)
    new ConversationsUiServiceImpl(selfUserId, teamId, domain, assetService, users, messages, messagesStorage,
      msgContent, members, content, convsStorage, network, convsService, sync, requests, client,
      accounts, tracking, errors, uriHelper, properties)
  }

  feature("Read receipts should match the spec") {
    scenario("Group conversation setting on means RR are always sent") {
      val convId = ConvId("test")
      val rrSettings = ReadReceiptSettings(selfSettings = false, Some(1))
      val service = getService().asInstanceOf[ConversationsUiServiceImpl]

      (convsService.isGroupConversation _).expects(convId).once().returning(Future.successful(true))

      Await.result(service.shouldSendReadReceipts(convId, rrSettings), 1.second) shouldEqual true
    }

    scenario("Group conversation setting off means RR are never sent ") {
      val convId = ConvId("test")
      val rrSettings = ReadReceiptSettings(selfSettings = true, Some(0))
      val service = getService().asInstanceOf[ConversationsUiServiceImpl]

      (convsService.isGroupConversation _).expects(convId).once().returning(Future.successful(true))

      Await.result(service.shouldSendReadReceipts(convId, rrSettings), 1.second) shouldEqual false
    }

    scenario("1-to-1 self settings set to false means RR are never sent") {
      val convId = ConvId("test")
      val rrSettings = ReadReceiptSettings(selfSettings = false, None)
      val service = getService().asInstanceOf[ConversationsUiServiceImpl]

      (convsService.isGroupConversation _).expects(convId).once().returning(Future.successful(false))

      Await.result(service.shouldSendReadReceipts(convId, rrSettings), 1.second) shouldEqual false
    }

    scenario("1-to-1 self settings set to true means RR are sent") {
      val convId = ConvId("test")
      val rrSettings = ReadReceiptSettings(selfSettings = true, None)
      val service = getService().asInstanceOf[ConversationsUiServiceImpl]

      (convsService.isGroupConversation _).expects(convId).once().returning(Future.successful(false))

      Await.result(service.shouldSendReadReceipts(convId, rrSettings), 1.second) shouldEqual true
    }
  }
}
