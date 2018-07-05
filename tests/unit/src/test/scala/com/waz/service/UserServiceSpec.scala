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

import com.waz.content._
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.{Availability, _}
import com.waz.service.assets.AssetService
import com.waz.service.conversation.ConversationsListStateService
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.{CredentialsUpdateClient, UsersClient}
import com.waz.testutils.TestUserPreferences
import com.waz.utils.events.{BgEventSource, Signal, SourceSignal}
import org.threeten.bp.Instant

import scala.concurrent.Future

class UserServiceSpec extends AndroidFreeSpec {

  private lazy val me = UserData(name = "me").updateConnectionStatus(ConnectionStatus.Self)

  private lazy val users = Seq(me, UserData("other user 1"), UserData("other user 2"), UserData("some name"),
    UserData("related user 1"), UserData("related user 2"), UserData("other related"),
    UserData("friend user 1"), UserData("friend user 2"), UserData("some other friend")
  )

  val accountsService = mock[AccountsService]
  val accountsStrg    = mock[AccountStorage]
  val usersStorage    = mock[UsersStorage]
  val membersStorage  = mock[MembersStorage]
  val pushService     = mock[PushService]
  val assetService    = mock[AssetService]
  val usersClient     = mock[UsersClient]
  val sync            = mock[SyncServiceHandle]
  val database        = mock[Database]
  val assetsStorage   = mock[AssetsStorage]
  val credentials     = mock[CredentialsUpdateClient]
  val stats           = mock[ConversationsListStateService]
  val userPrefs       = new TestUserPreferences

  (usersStorage.optSignal _).expects(*).anyNumberOfTimes().onCall((id: UserId) => Signal.const(users.find(_.id == id)))
  (accountsService.accountsWithManagers _).expects().anyNumberOfTimes().returning(Signal.empty)
  (pushService.onHistoryLost _).expects().anyNumberOfTimes().returning(new SourceSignal(Some(Instant.now())) with BgEventSource)
  (sync.syncUsers _).expects(*).anyNumberOfTimes().returning(Future.successful(SyncId()))
  (assetService.updateAssets _).expects(*).anyNumberOfTimes().returning(Future.successful(Set.empty))
  (usersStorage.updateOrCreateAll _).expects(*).anyNumberOfTimes().returning(Future.successful(Set.empty))
  (stats.selectedConversationId _).expects().anyNumberOfTimes().returning(Signal.const(None))

  private def getService = {

    result(userPrefs(UserPreferences.ShouldSyncUsers) := false)

    new UserServiceImpl(
      users.head.id, None, accountsService, accountsStrg, usersStorage, membersStorage,
      userPrefs, pushService, assetService, usersClient, sync, assetsStorage, credentials, stats
    )
  }

  feature("activity status") {
    scenario("change activity status") {

      val id = me.id
      val availability = me.availability
      availability should not equal Availability.Busy

      val before = me.copy()
      val after = me.copy(availability = Availability.Busy)
      (usersStorage.update _).expects(id, *).once().onCall { (id, updater) =>
        updater(before) shouldEqual after
        Future.successful(Some((before, after)))
      }
      (sync.postAvailability _).expects(Availability.Busy).once().returning(Future.successful(SyncId()))

      val service = getService
      result(service.updateAvailability(Availability.Busy))
    }
  }

  feature("load user") {

    scenario("update self user") {

      val id = users.head.id
      (usersStorage.get _).expects(id).once().returning(Future.successful(users.headOption))

      val service = getService
      result(service.updateSyncedUsers(Seq(UserInfo(id))))
      result(service.getSelfUser).map(_.connection) shouldEqual Some(ConnectionStatus.Self)
    }
  }
}
