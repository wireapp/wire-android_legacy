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
package com.waz.zclient.controllers

import android.content.Context
import android.view.View
import com.waz.model.MessageId
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.profile.ZetaPreferencesActivity
import com.waz.zclient.{Injectable, Injector}

class ScreenController(implicit injector: Injector, context: Context) extends Injectable {

  val zms = inject[Signal[ZMessaging]]
  val conversationController = inject[IConversationScreenController]

  def openOtrDevicePreferences() =
    context.startActivity(ZetaPreferencesActivity.getOtrDevicesPreferencesIntent(context))

  def showParticipants(anchorView: View, showDeviceTabIfSingle: Boolean = false) =
    conversationController.showParticipants(anchorView, showDeviceTabIfSingle)

  def showUsersWhoLike(mId: MessageId) = conversationController.showLikesList(ZMessaging.currentUi.messages.cachedOrNew(mId))

}
