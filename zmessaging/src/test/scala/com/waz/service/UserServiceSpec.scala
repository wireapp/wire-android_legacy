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
import com.waz.service.assets.{AssetService, AssetStorage}
import com.waz.service.conversation.SelectedConversationService
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.{CredentialsUpdateClient, UsersClient}
import com.waz.testutils.TestUserPreferences
import com.wire.signals.{CancellableFuture, Signal, SourceSignal}
import com.waz.threading.Threading
import com.waz.zms.BuildConfig
import org.threeten.bp.Instant

import scala.concurrent.Future

class UserServiceSpec extends AndroidFreeSpec {

  private val domain = if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("staging.zinfra.io") else Domain.Empty
  private val otherDomain = if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("chala.wire.link") else Domain.Empty

  private lazy val me = UserData(name = "me").updateConnectionStatus(ConnectionStatus.Self).copy(domain = domain)
  private lazy val user1 = UserData("other user 1")
  private lazy val federatedUser = UserData("federated user").copy(domain = otherDomain)
  private lazy val meAccount = AccountData(me.id)

  private lazy val users = Seq(me, user1, UserData("other user 2"), UserData("some name"),
    UserData("related user 1"), UserData("related user 2"), UserData("other related"),
    UserData("friend user 1"), UserData("friend user 2"), UserData("some other friend"),
    federatedUser
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
  val assetsStorage   = mock[AssetStorage]
  val credentials     = mock[CredentialsUpdateClient]
  val selectedConv    = mock[SelectedConversationService]
  val messages        = mock[MessagesService]
  val userPrefs       = new TestUserPreferences

  (usersStorage.optSignal _).expects(*).anyNumberOfTimes().onCall((id: UserId) => Signal.const(users.find(_.id == id)))
  (accountsService.accountsWithManagers _).expects().anyNumberOfTimes().returning(Signal.empty)
  (pushService.onHistoryLost _).expects().anyNumberOfTimes().returning(SourceSignal(Instant.now()))
  (sync.syncUsers _).expects(*).anyNumberOfTimes().returning(Future.successful(SyncId()))
  (selectedConv.selectedConversationId _).expects().anyNumberOfTimes().returning(Signal.const(None))

  private def getService = {

    result(userPrefs(UserPreferences.ShouldSyncUsers) := false)

    new UserServiceImpl(
      users.head.id, domain, None, accountsService, accountsStrg, usersStorage, membersStorage,
      userPrefs, pushService, assetService, usersClient, sync, assetsStorage, credentials,
      selectedConv, messages
    )
  }

  val completionHandlerThatExecutesFunction: (() => Future[_]) => Future[Unit] = { f => f().map(_ => {})(Threading.Background) }
  val completionHandlerThatDoesNotExecuteFunction: (() => Future[_]) => Future[Unit] = { _ => Future.successful({}) }


  feature("activity status") {

    scenario("it does propagate activity status if team size is smaller than threshold") {

      //given
      val id = me.id
      val teamId = TeamId("Wire")
      val someTeamId = Some(teamId)
      val availability = me.availability
      availability should not equal Availability.Busy

      val userService = new UserServiceImpl(
        users.head.id, domain, someTeamId, accountsService, accountsStrg, usersStorage, membersStorage,
        userPrefs, pushService, assetService, usersClient, sync, assetsStorage, credentials,
        selectedConv, messages
      )

      //expect
      val before = me.copy()
      val after = me.copy(availability = Availability.Busy)

      (usersStorage.update _).expects(id, *).once().onCall { (_, updater) =>
        updater(before) shouldEqual after
        Future.successful(Some((before, after)))
      }

      (sync.postAvailability _).expects(after.availability).returning(Future.successful(SyncId()))

      //when
      result(userService.updateAvailability(Availability.Busy))
    }
  }

  feature("load user") {

    scenario("update self user") {
      val id = users.head.id
      (usersStorage.updateOrCreateAll _).expects(*).anyNumberOfTimes().returning(Future.successful(Set.empty))
      (usersStorage.get _).expects(id).once().returning(Future.successful(users.headOption))

      val service = getService
      result(service.updateSyncedUsers(Seq(UserInfo(id))))
      result(service.getSelfUser).map(_.connection) shouldEqual Some(ConnectionStatus.Self)
    }

    scenario("check if user exists") {
      val userInfo = UserInfo(user1.id)
      (usersClient.loadUser _).expects(user1.id).anyNumberOfTimes().returning(
        CancellableFuture.successful(Right(Option(userInfo)))
      )
      (usersStorage.get _).expects(user1.id).anyNumberOfTimes().returning(Future.successful(Option(user1)))
      (usersStorage.updateOrCreateAll _).expects(*).anyNumberOfTimes().returning(Future.successful(Set(user1)))

      val service = getService
      result(service.syncUser(user1.id)) shouldEqual Some(user1)
    }

    scenario("check if a federated user exists") {
      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        val qId = federatedUser.qualifiedId.get
        val userInfo = UserInfo(federatedUser.id, domain = otherDomain)

        (usersClient.loadQualifiedUser _).expects(qId).anyNumberOfTimes().returning(
          CancellableFuture.successful(Right(Option(userInfo)))
        )
        (usersStorage.get _).expects(federatedUser.id).anyNumberOfTimes().returning(Future.successful(Option(federatedUser)))
        (usersStorage.updateOrCreateAll _).expects(*).anyNumberOfTimes().returning(Future.successful(Set(federatedUser)))

        val service = getService
        result(service.syncUser(federatedUser.id)) shouldEqual Some(federatedUser)
      }
    }

    scenario("delete user locally if it the client says it's removed") {
      val convId = ConvId()
      val member = ConversationMemberData(user1.id, convId, ConversationRole.AdminRole)
      (usersClient.loadUser _).expects(user1.id).anyNumberOfTimes().returning(
        CancellableFuture.successful(Right(None))
      )
      (usersStorage.updateOrCreateAll _).expects(*).never()
      (membersStorage.getByUsers _).expects(Set(user1.id)).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(member))
      )
      (membersStorage.removeAll _).expects(Set(member.id)).atLeastOnce().returning(
        Future.successful(())
      )
      (messages.addMemberLeaveMessage _).expects(convId, *, Set(user1.id), *).atLeastOnce().returning(
        Future.successful(())
      )
      (usersStorage.updateAll2 _).expects(Set(user1.id), *).atLeastOnce().onCall { (_: Iterable[UserId], updater: UserData => UserData) =>
        Future.successful(Seq((user1, updater(user1))))
      }
      (usersStorage.get _).expects(user1.id).anyNumberOfTimes().returning(Future.successful(Option(user1)))

      val service = getService
      result(service.syncUser(user1.id)) shouldEqual None
    }
  }
}
