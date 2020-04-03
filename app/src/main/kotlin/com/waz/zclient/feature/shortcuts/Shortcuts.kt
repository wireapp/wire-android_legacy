package com.waz.zclient.feature.shortcuts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import com.waz.zclient.R

@SuppressLint("NewApi")
class Shortcuts {

    companion object {
        const val NEW_GROUP_CONVERSATION = "GROUP_CONVERSATION"
        const val NEW_MESSAGE = "NEW_MESSAGE"
        const val SHARE_PHOTO = "SHARE_PHOTO"
        const val SHARE_PHOTO_REQUEST_CODE = 40
    }

    fun newMessageShortcut(activity: Activity, intent: Intent): ShortcutInfo =
        ShortcutInfo.Builder(activity, "new_message")
            .setShortLabel(activity.getString(R.string.shortcut_new_message_label))
            .setLongLabel(activity.getString(R.string.shortcut_new_message_label))
            .setIcon(Icon.createWithResource(activity, R.drawable.ic_create_conversation))
            .setIntent(intent.setAction(NEW_MESSAGE))
            .build()

    fun sharePhotoShortcut(activity: Activity, intent: Intent): ShortcutInfo =
        ShortcutInfo.Builder(activity, "share_photo")
            .setShortLabel(activity.getString(R.string.shortcut_share_a_photo_label))
            .setLongLabel(activity.getString(R.string.shortcut_share_a_photo_label))
            .setIcon(Icon.createWithResource(activity, R.drawable.ic_share_photo))
            .setIntent(intent.setAction(SHARE_PHOTO))
            .build()

    fun groupConversationShortcut(activity: Activity, intent: Intent): ShortcutInfo =
        ShortcutInfo.Builder(activity, "new_group")
            .setShortLabel(activity.getString(R.string.shortcut_create_group_label))
            .setLongLabel(activity.getString(R.string.shortcut_create_group_label))
            .setIcon(Icon.createWithResource(activity, R.drawable.ic_create_group))
            .setIntent(intent.setAction(NEW_GROUP_CONVERSATION))
            .build()
}
