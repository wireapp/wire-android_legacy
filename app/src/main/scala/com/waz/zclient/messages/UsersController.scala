/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.messages

import android.content.Context
import com.waz.content.{MembersStorage, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType.isOneToOne
import com.waz.model._
import com.waz.service.{ConnectionService, UserService, ZMessaging}
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.UsersController._
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.BuildConfig

import scala.concurrent.Future

class UsersController(implicit injector: Injector, context: Context)
  extends Injectable with DerivedLogTag {

  private lazy val zms               = inject[Signal[ZMessaging]]
  private lazy val membersStorage    = inject[Signal[MembersStorage]]
  private lazy val connectionService = inject[Signal[ConnectionService]]
  private lazy val selfUserId        = inject[Signal[UserId]]
  private lazy val userService       = inject[Signal[UserService]]

  private lazy val selfDomain        = inject[Domain]

  private lazy val itemSeparator = getString(R.string.content__system__item_separator)
  private lazy val lastSeparator = getString(R.string.content__system__last_item_separator)

  //Always returns the other user for the conversation for a given message, regardless of who sent the message
  def getOtherUser(message: Signal[MessageData]): Signal[Option[UserData]] = for {
    zms <- zms
    msg <- message
    conv <- zms.convsStorage.signal(msg.convId)
    members <- membersStorage.flatMap(_.activeMembers(conv.id))
    userId = if (isOneToOne(conv.convType)) Some(UserId(conv.id.str)) else members.find(_ != zms.selfUserId)
    user <- userId.fold(Signal.const(Option.empty[UserData]))(uId => user(uId).map(Some(_)))
  } yield user

  // this is the same as ConversationController.DefaultDeletedName but it's possible just as well
  // that the default name for a conversation is different from the default name for a user
  lazy val DefaultDeletedName: Name = Name(getString(R.string.default_deleted_username))

  def displayName(id: UserId): Signal[DisplayName] = selfUserId.flatMap {
    case selfId if selfId == id =>
      Signal.const[DisplayName](Me)
    case _ =>
      userService.flatMap(_.userNames.map(_.getOrElse(id, DefaultDeletedName)).map(Other(_)))
  }

  def displayHandle(id: UserId): Future[String] = {
    import Threading.Implicits.Background
    for {
      service    <- userService.head
      Some(user) <- service.findUser(id)
    } yield displayHandle(user)
  }

  def displayHandle(user: UserData): String = user.displayHandle(selfDomain)

  def syncUserAndCheckIfDeleted(userId: UserId): Future[(Option[UserData], Option[UserData])] = {
    import Threading.Implicits.Background
    for {
      service <- userService.head
      oldUser <- service.findUser(userId)
      newUser <- if (oldUser.nonEmpty) service.syncUser(userId) else Future.successful(None)
    } yield (oldUser, newUser)
  }

  lazy val availabilityVisible: Signal[Boolean] = for {
    selfId <- selfUserId
    self   <- user(selfId)
  } yield self.teamId.nonEmpty

  def availability(userId: UserId): Signal[Availability] = for {
    avVisible <- availabilityVisible
    otherUser <- if (avVisible) user(userId).map(Option(_)) else Signal.const(Option.empty[UserData])
  } yield {
    otherUser.fold[Availability](Availability.None)(_.availability)
  }

  def updateAvailability(availability: Availability): Future[Unit] = {
    verbose(l"updateAvailability $availability")
    import Threading.Implicits.Ui
    for {
      zms   <- zms.head
      prefs <- inject[Signal[UserPreferences]].head
      mask  <- prefs(UserPreferences.StatusNotificationsBitmask).apply()
    } yield {
      verbose(l"mask = $mask, bit = ${availability.bitmask}, res = ${mask & availability.bitmask}")
      if ((mask & availability.bitmask) == 0) {
        inject[AccentColorController].accentColor.head.foreach { color =>
          showStatusNotificationWarning(availability, color).foreach {
            if (_) prefs(UserPreferences.StatusNotificationsBitmask).mutate(_ | availability.bitmask)
          }
        }
      }
      zms.users.updateAvailability(availability)
    }
  }

  def accentColor(id: UserId): Signal[AccentColor] = user(id).map(u => AccentColor(u.accent))

  def memberIsJustSelf(message: Signal[MessageData]): Signal[Boolean] ={
    for {
      zms <- zms
      msg <- message
    } yield msg.members.size == 1 && msg.members.contains(zms.selfUserId)
  }

  def getMemberNames(members: Set[UserId]): Signal[Seq[DisplayName]] = Signal.sequence(members.toSeq.map(displayName): _*)

  def getMemberNamesSplit(members: Set[UserId], self: UserId): Signal[MemberNamesSplit] =
    for {
      names          <- getMemberNames(members).map(_.collect { case o @ Other(_) => o }.sortBy(_.name))
      (main, others) =  if (names.size > MaxStringMembers) names.splitAt(MaxStringMembers - 2) else (names, Seq.empty)
    } yield MemberNamesSplit(main, others, members.contains(self))

  def membersNamesString(membersNames: Seq[DisplayName], separateLast: Boolean = true, boldNames: Boolean = false): String = {
    val strings = membersNames.map {
      case Other(name) => if (boldNames) s"[[$name]]" else name
      case Me => if (boldNames) s"[[${getString(R.string.content__system__you)}]]" else getString(R.string.content__system__you)
    }
    if (separateLast && strings.size > 1)
      s"${strings.take(strings.size - 1).mkString(itemSeparator + " ")} $lastSeparator ${strings.last}"
    else
      strings.mkString(itemSeparator + " ")
  }

  def user(id: UserId): Signal[UserData] = zms.flatMap(_.usersStorage.signal(id))
  def userOpt(id: UserId): Signal[Option[UserData]] = zms.flatMap(_.usersStorage.optSignal(id))
  def users(ids: Iterable[UserId]): Signal[Vector[UserData]] = zms.flatMap(_.usersStorage.listSignal(ids))

  def selfUser: Signal[UserData] = selfUserId.flatMap(user)

  def isFederated(user: UserData): Boolean =
    BuildConfig.FEDERATION_USER_DISCOVERY && user.domain != selfDomain

  def conv(msg: MessageData): Signal[ConversationData] =
    for {
      zms  <- zms
      conv <- zms.convsStorage.signal(msg.convId)
    } yield conv

  def connectToUser(userId: UserId): Future[Option[ConversationData]] = {
    import Threading.Implicits.Background
    for {
      connection <- connectionService.head
      self       <- selfUser.head
      otherUser  <- user(userId).head
      message    =  getString(R.string.connect__message, otherUser.name, self.name)
      conv       <- connection.connectToUser(userId, message, otherUser.name)
    } yield conv
  }

  def cancelConnectionRequest(userId: UserId): Future[Unit] = {
    import Threading.Implicits.Background
    for {
      connection <- connectionService.head
      _          <- connection.cancelConnection(userId)
    } yield ()
  }

  def ignoreConnectionRequest(userId: UserId): Future[Unit] = {
    import Threading.Implicits.Background
    for {
      connection <- connectionService.head
      _          <- connection.ignoreConnection(userId)
    } yield ()
  }

  def unblockUser(userId: UserId): Future[Unit] = {
    import Threading.Implicits.Background
    for {
      connection <- connectionService.head
      _          <- connection.unblockConnection(userId)
    } yield ()
  }
}

object UsersController {
  val MaxStringMembers: Int = 17

  case class MemberNamesSplit(main: Seq[Other], others: Seq[Other], andYou: Boolean) {
    val shorten: Boolean = others.nonEmpty
  }

  sealed trait DisplayName
  object DisplayName {
    case object Me extends DisplayName
    case class Other(name: String) extends DisplayName
  }

}
