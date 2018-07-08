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
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.AccentColor
import com.waz.model.ConversationData.ConversationType.isOneToOne
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.messages.UsersController.DisplayName
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.tracking.AvailabilityChanged
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.Future

class UsersController(implicit injector: Injector, context: Context) extends Injectable {

  private val zMessaging = inject[Signal[ZMessaging]]
  private val tracking   = inject[TrackingService]

  lazy val itemSeparator = getString(R.string.content__system__item_separator)
  lazy val lastSeparator = getString(R.string.content__system__last_item_separator)

  lazy val selfUserId = zMessaging map { _.selfUserId }

  //Always returns the other user for the conversation for a given message, regardless of who sent the message
  def getOtherUser(message: Signal[MessageData]): Signal[Option[UserData]] = for {
    zms <- zMessaging
    msg <- message
    conv <- zms.convsStorage.signal(msg.convId)
    user <- user(UserId(conv.id.str))
  } yield if (isOneToOne(conv.convType)) Some(user) else None

  def displayNameStringIncludingSelf(id: UserId): Signal[String] =
    for {
      zms <- zMessaging
      user <- user(id)
    } yield user.getDisplayName

  def displayName(id: UserId): Signal[DisplayName] = zMessaging.flatMap { zms =>
    if (zms.selfUserId == id) Signal const Me
    else user(id).map(u => Other(u.getDisplayName))
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
    import Threading.Implicits.Background
    for {
      zms     <- zMessaging.head
      _       <- zms.users.updateAvailability(availability)
    } yield ()
  }

  def accentColor(id: UserId): Signal[AccentColor] = user(id).map(u => AccentColor(u.accent))

  def memberIsJustSelf(message: Signal[MessageData]): Signal[Boolean] ={
    for {
      zms <- zMessaging
      msg <- message
    } yield msg.members.size == 1 && msg.members.contains(zms.selfUserId)
  }

  def memberDisplayNames(message: Signal[MessageData], boldNames: Boolean = false) =
    for {
      zms <- zMessaging
      msg <- message
      names <- Signal.sequence(msg.members.toSeq.sortBy(_.str).map(displayName): _*)
        .map(_.map {
          case Me          => getString(R.string.content__system__you)
          case Other(name) => name
        }.map(name => if (boldNames) s"[[$name]]" else name))
    } yield
      names match {
        case Seq() => ""
        case Seq(name) => name
        case _ =>
          val n = names.length
          s"${names.take(n - 1).mkString(itemSeparator + " ")} $lastSeparator ${names.last}"
      }

  def userHandle(id: UserId): Signal[Option[Handle]] = user(id).map(_.handle)

  def user(id: UserId): Signal[UserData] = zMessaging flatMap { _.usersStorage.signal(id) }

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
      conv <- zms.connection.connectToUser(userId, message, uToConnect.displayName)
    } yield conv
  }
}

object UsersController {

  sealed trait DisplayName
  object DisplayName {
    case object Me extends DisplayName
    case class Other(name: String) extends DisplayName
  }

}
