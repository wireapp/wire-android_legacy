/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.shortcuts

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import com.waz.zclient.R

object Shortcuts {

  val NewGroupConversation  = "GROUP_CONVERSATION"
  val NewMessage            = "NEW_MESSAGE"
  val SharePhoto            = "SHARE_PHOTO"
  val SharePhotoRequestCode = 40

  def newMessageShortcut(activity: Activity, intent: Intent): ShortcutInfo =
    new ShortcutInfo.Builder(activity, "new_message")
      .setShortLabel(activity.getString(R.string.shortcut_new_message_label))
      .setLongLabel(activity.getString(R.string.shortcut_new_message_label))
      .setIcon(Icon.createWithResource(activity, R.drawable.ic_create_conversation))
      .setIntent(intent.setAction(NewMessage))
      .build


  def sharePhotoShortcut(activity: Activity, intent: Intent): ShortcutInfo =
    new ShortcutInfo.Builder(activity, "share_photo")
      .setShortLabel(activity.getString(R.string.shortcut_share_a_photo_label))
      .setLongLabel(activity.getString(R.string.shortcut_share_a_photo_label))
      .setIcon(Icon.createWithResource(activity, R.drawable.ic_share_photo))
      .setIntent(intent.setAction(SharePhoto))
      .build

  def groupConversationShortcut(activity: Activity, intent: Intent): ShortcutInfo =
    new ShortcutInfo.Builder(activity, "new_group")
      .setShortLabel(activity.getString(R.string.shortcut_create_group_short_label))
      .setLongLabel(activity.getString(R.string.shortcut_create_group_long_label))
      .setIcon(Icon.createWithResource(activity, R.drawable.ic_create_group))
      .setIntent(intent.setAction(NewGroupConversation))
      .build

}
