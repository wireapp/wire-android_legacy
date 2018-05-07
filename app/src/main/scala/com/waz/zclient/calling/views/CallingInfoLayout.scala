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
package com.waz.zclient.calling.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.verbose
import com.waz.utils.events.Signal
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class CallingInfoLayout(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) =  this(context, null)

  private lazy val nameView: TextView = findById(R.id.ttv__calling__header__name)
  private lazy val subtitleView: TextView = findById(R.id.ttv__calling__header__subtitle)
  private lazy val bitRateModeView: TextView = findById(R.id.ttv__calling__header__bitrate)
  private lazy val chathead = findById[ChatheadView](R.id.call_chathead)

  private lazy val participants = findById[CallParticipantsView](R.id.call_participants)

  LayoutInflater.from(context).inflate(R.layout.calling_info, this, true)
  setOrientation(LinearLayout.HORIZONTAL)

  val controller = inject[CallController]

  controller.subtitleText.onUi(subtitleView.setText)

  controller.conversationName.onUi(nameView.setText)

  controller.cbrEnabled.map {
    case true => getString(R.string.audio_message__constant_bit_rate)
    case false => ""
  }.onUi(bitRateModeView.setText)

  Signal(controller.isCallIncoming, controller.isCallEstablished,  controller.showVideoView, controller.isGroupCall)
    .map { case (in, est, video, group) => (in || est)  && !video && !group }
    .onUi { visible =>
      verbose(s"chathead visible: $visible")
      chathead.setVisible(visible)
    }

  controller.otherUser.map(_.map(_.id)).onUi {
    case Some(userId) => chathead.setUserId(userId)
    case None =>
  }

  Signal(controller.isCallEstablished,  controller.showVideoView, controller.isGroupCall)
    .map { case (est, video, group) => est && !video && group }
    .onUi { visible =>
      verbose(s"participants visible: $visible")
      participants.setVisible(visible)
    }

}
