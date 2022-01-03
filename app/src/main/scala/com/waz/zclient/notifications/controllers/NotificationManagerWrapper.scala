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
package com.waz.zclient.notifications.controllers

import android.app.NotificationChannel
import com.waz.model.{ConvId, UserId}

trait NotificationManagerWrapper {
  def showNotification(notificationProps: NotificationProps): Unit
  def cancelNotifications(accountId: UserId, convs: Set[ConvId]): Unit
  def getNotificationChannel(channelId: String): Option[NotificationChannel]
}

object NotificationManagerWrapper {
  val WireNotificationsChannelGroupId    = "WIRE_NOTIFICATIONS_CHANNEL_GROUP"
  val WireNotificationsChannelGroupName  = "Wire Notifications Channel Group"
  val IncomingCallNotificationsChannelId = "INCOMING_CALL_NOTIFICATIONS_CHANNEL_ID"
  val OngoingNotificationsChannelId      = "STICKY_NOTIFICATIONS_CHANNEL_ID"
  val PingNotificationsChannelId         = "PINGS_NOTIFICATIONS_CHANNEL_ID"
  val MessageNotificationsChannelId      = "MESSAGE_NOTIFICATIONS_CHANNEL_ID"
}
