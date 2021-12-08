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
package com.waz.model

import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType.LikedContent
import com.waz.utils.Identifiable

final case class NotificationData(conv:              ConvId,
                                  user:              UserId,
                                  msg:               String               = "",
                                  msgType:           NotificationType     = NotificationType.TEXT,
                                  time:              RemoteInstant        = RemoteInstant.Epoch,
                                  ephemeral:         Boolean              = false,
                                  isSelfMentioned:   Boolean              = false,
                                  likedContent:      Option[LikedContent] = None,
                                  isReply:           Boolean              = false,
                                  hasBeenDisplayed:  Boolean              = false) {
  lazy val isConvDeleted: Boolean = msgType == NotificationType.CONVERSATION_DELETED
}

