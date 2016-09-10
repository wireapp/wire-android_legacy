/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.messages

import android.content.Context
import com.waz.api.impl.AccentColor
import com.waz.model.{MessageData, UserId}
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.messages.SyncEngineSignals.DisplayName
import com.waz.zclient.messages.SyncEngineSignals.DisplayName.{Me, Other}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}

class SyncEngineSignals(implicit injector: Injector, context: Context) extends Injectable {

  private val zMessaging = inject[Signal[ZMessaging]]

  lazy val itemSeparator = getString(R.string.content__system__item_separator)
  lazy val lastSeparator = getString(R.string.content__system__last_item_separator)

  def displayName(zms: ZMessaging, id: UserId): Signal[String] =
    if (zms.selfUserId == id) Signal const getString(R.string.content__system__you)
    else zms.users.userSignal(id).map(_.getDisplayName)

  def userDisplayName(zms: ZMessaging, id: UserId): Signal[DisplayName] =
    if (zms.selfUserId == id) Signal const Me
    else zms.users.userSignal(id).map(u => Other(u.getDisplayName))

  def userDisplayName(message: Signal[MessageData]): Signal[DisplayName] =
    for {
      zms <- zMessaging
      msg <- message
      name <- userDisplayName(zms, msg.userId)
    } yield name

  def userDisplayNameString(message: Signal[MessageData]): Signal[String] =
    for {
      zms <- zMessaging
      msg <- message
      name <- displayName(zms, msg.userId)
    } yield name

  def userAccentColor(message: Signal[MessageData]) =
    for {
      zms <- zMessaging
      msg <- message
      user <- zms.users.userSignal(msg.userId)
    } yield AccentColor(user.accent)

  def memberDisplayNames(message: Signal[MessageData]) =
    for {
      zms <- zMessaging
      msg <- message
      names <- Signal.sequence[String](msg.members.toSeq.sortBy(_.str).map { displayName(zms, _) }: _*)
    } yield
      names match {
        case Seq() => ""
        case Seq(name) => name
        case _ =>
          val n = names.length
          s"${names.take(n - 1).mkString(itemSeparator + " ")} $lastSeparator  ${names.last}"
      }

  def user(id: UserId) = zMessaging flatMap { _.users.userSignal(id) }

  def selfUserId = zMessaging map { _.selfUserId }
}

object SyncEngineSignals {

  sealed trait DisplayName
  object DisplayName {
    case object Me extends DisplayName
    case class Other(name: String) extends DisplayName
  }
}
