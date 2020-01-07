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
import com.waz.service.{ConnectionService, ZMessaging}
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.UsersController._
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.tracking.AvailabilityChanged
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}

import com.waz.zclient.log.LogUI._

import scala.concurrent.Future

class UsersController(implicit injector: Injector, context: Context)
  extends Injectable with DerivedLogTag {

  private lazy val zMessaging = inject[Signal[ZMessaging]]
  private lazy val tracking   = inject[TrackingService]
  private lazy val membersStorage = inject[Signal[MembersStorage]]

  private lazy val itemSeparator = getString(R.string.content__system__item_separator)
  private lazy val lastSeparator = getString(R.string.content__system__last_item_separator)

  lazy val selfUserId = zMessaging map { _.selfUserId }

  //Always returns the other user for the conversation for a given message, regardless of who sent the message
  def getOtherUser(message: Signal[MessageData]): Signal[Option[UserData]] = for {
    zms <- zMessaging
    msg <- message
    conv <- zms.convsStorage.signal(msg.convId)
    members <- membersStorage.flatMap(_.activeMembers(conv.id))
    userId = if (isOneToOne(conv.convType)) Some(UserId(conv.id.str)) else members.find(_ != zms.selfUserId)
    user <- userId.fold(Signal.const(Option.empty[UserData]))(uId => user(uId).map(Some(_)))
  } yield user

  def displayName(id: UserId): Signal[DisplayName] = zMessaging.flatMap { zms =>
    if (zms.selfUserId == id) Signal const Me
    else user(id).map(u => Other(if (u.deleted) getString(R.string.default_deleted_username) else u.name))
  }

  lazy val availabilityVisible: Signal[Boolean] = for {
    selfId <- selfUserId
    self <- user(selfId)
  } yield self.teamId.nonEmpty

  def availability(userId: UserId): Signal[Availability] = for {
    avVisible <- availabilityVisible
    otherUser <- if (avVisible) user(userId).map(Option(_)) else Signal.const(Option.empty[UserData])
  } yield {
    otherUser.fold[Availability](Availability.None)(_.availability)
  }

  def trackAvailability(availability: Availability, method: AvailabilityChanged.Method): Unit =
    tracking.track(AvailabilityChanged(availability, method))

  def updateAvailability(availability: Availability): Future[Unit] = {
    verbose(l"updateAvailability $availability")
    import Threading.Implicits.Ui
    for {
      zms   <- zMessaging.head
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
      zms <- zMessaging
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

  def userHandle(id: UserId): Signal[Option[Handle]] = user(id).map(_.handle)

  def user(id: UserId): Signal[UserData] = zMessaging.flatMap(_.usersStorage.signal(id))
  def userOpt(id: UserId): Signal[Option[UserData]] = zMessaging.flatMap(_.usersStorage.optSignal(id))
  def users(ids: Iterable[UserId]): Signal[Vector[UserData]] = zMessaging.flatMap(_.usersStorage.listSignal(ids))

  def selfUser: Signal[UserData] = selfUserId.flatMap(user)

  def conv(msg: MessageData) = {
    for {
      zms <- zMessaging
      conv <- zms.convsStorage.signal(msg.convId)
    } yield conv
  }

  def connectToUser(userId: UserId): Future[Option[ConversationData]] = {
    import Threading.Implicits.Background
    for {
      uSelf <- selfUser.head
      uToConnect <- user(userId).head
      zms <- zMessaging.head
      message = getString(R.string.connect__message, uToConnect.name, uSelf.name)
      conv <- zms.connection.connectToUser(userId, message, uToConnect.name)
    } yield conv
  }

  def cancelConnectionRequest(userId: UserId): Future[Unit] = {
    import Threading.Implicits.Background
    for {
      connection <- inject[Signal[ConnectionService]].head
      _ <- connection.cancelConnection(userId)
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
