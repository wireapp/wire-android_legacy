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

import com.waz.content.{MembersStorage, UsersStorage}
import com.waz.model._
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.wire.signals.{EventStream, Signal}
import org.threeten.bp.Duration

import scala.concurrent.Future
import scala.concurrent.duration._

class ExpiredUsersServiceSpec extends AndroidFreeSpec {

  implicit val ec = Threading.Background
  val push      = mock[PushService]
  val members   = mock[MembersStorage]
  val users     = mock[UserService]
  val usersStorage = mock[UsersStorage]
  val sync      = mock[SyncServiceHandle]

  val onDeleted = EventStream[Seq[(UserId, ConvId)]]
  val currentConv = Signal(Option.empty[ConvId])

  val currentDomain = Domain("staging.zinfra.io")

  //All user expiry times have an extra 10 seconds to factor in the buffer we leave in the service
  scenario("Start timer for user soon to expire") {
    val conv = ConvId("conv")
    val wirelessId = UserId("wirelessUser")
    val wirelessUser = UserData("wireless").copy(id = wirelessId, domain = currentDomain, expiresAt = Some(RemoteInstant(clock.instant()) - 10.seconds + 200.millis))
    val finished = EventStream[Unit]()
    val convUsers = Set(
      UserData("user1").copy(id = UserId("user1"), domain = currentDomain),
      UserData("user2").copy(id = UserId("user2"), domain = currentDomain),
      wirelessUser
    )
    val convSignals = convUsers.map(u => u.id -> Signal.const(u)).toMap

    (users.syncUser(_ : UserId)).expects(*).once().onCall { _: UserId =>
      finished ! {}
      Future.successful(Some(wirelessUser))
    }

    (users.currentConvMembers _).expects().once().returning(Signal.const(convUsers.map(_.id)))
    (usersStorage.signal _).expects(*).anyNumberOfTimes().onCall { id: UserId => convSignals(id) }

    val service = getService //trigger creation of service
    currentConv ! Some(conv)
    clock + 10.seconds
    result(finished.next)
  }

  scenario("Start timer for user soon to expire, user is removed elsewhere") {
    val conv = ConvId("conv")

    currentConv ! Some(conv)

    val wirelessId = UserId("wirelessUser")

    val convUsers = Set(
      UserData("user1").copy(id = UserId("user1")),
      UserData("user2").copy(id = UserId("user2")),
      UserData("wireless").copy(id = wirelessId, expiresAt = Some(RemoteInstant(clock.instant()) - 10.seconds + 200.millis))
    )

    val currentConvMembers = Signal[Set[UserId]]()

    (users.currentConvMembers _).expects().once().returning(currentConvMembers)
    (usersStorage.signal _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
      convUsers.find(_.id == id).map(Signal.const).getOrElse(Signal.empty[UserData])
    }

    (members.getByUsers _).expects(Set(wirelessId)).once().returning(Future.successful(IndexedSeq.empty))

    getService //trigger creation of service

    Thread.sleep(100) //need to sleep to give timer a chance to be built...

    currentConvMembers ! (convUsers.map(_.id) - wirelessId)
    onDeleted ! Seq((wirelessId, conv))

    awaitAllTasks

    Thread.sleep(500)
    (users.syncUsers _).expects(*, *, *).never()
  }

  scenario("Wireless member added to conversation also triggers a timer") {
    val conv = ConvId("conv")
    val wirelessUser = UserData("wireless").copy(id = UserId("wirelessUser"), expiresAt = Some(RemoteInstant(clock.instant()) - 10.seconds + 200.millis))

    val convUsers = Set(
      UserData("user1").copy(id = UserId("user1")),
      UserData("user2").copy(id = UserId("user2"))
    )

    val activeMembers = Signal(convUsers.map(_.id))

    val finished = EventStream[Unit]()
    (users.syncUser(_ : UserId)).expects(wirelessUser.id).once().onCall { _: UserId =>
      finished ! {}
      Future.successful(Some(wirelessUser))
    }

    (users.currentConvMembers _).expects().anyNumberOfTimes().returning(activeMembers)
    (usersStorage.signal _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
      (convUsers + wirelessUser).find(_.id == id).map(Signal.const).getOrElse(Signal.empty[UserData])
    }

    currentConv ! Some(conv)

    getService //trigger creation of service

    activeMembers.mutate(_ + wirelessUser.id)

    awaitAllTasks

    result(finished.next)
  }

  def getService: ExpiredUsersService = {
    (members.onDeleted _).expects().once().returning(onDeleted)
    (push.beDrift _).expects().anyNumberOfTimes().returning(Signal.const(Duration.ZERO))

    new ExpiredUsersService(push, members, users, usersStorage, sync)
  }

}
