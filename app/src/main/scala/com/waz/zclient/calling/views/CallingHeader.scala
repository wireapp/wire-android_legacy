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
import android.view.{LayoutInflater, TextureView}
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog.verbose
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.api.VideoSendState
import com.waz.utils.events.Signal
import com.waz.zclient.calling.ControlsFragment
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.ui.calling.RoundedLayout
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class CallingHeader(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) =  this(context, null)

  private lazy val nameView: TextView = findById(R.id.ttv__calling__header__name)
  private lazy val subtitleView: TextView = findById(R.id.ttv__calling__header__subtitle)
  private lazy val bitRateModeView: TextView = findById(R.id.ttv__calling__header__bitrate)
  private lazy val roundedLayout = findById[RoundedLayout](R.id.rounded_layout)

  private val controller = inject[CallController]

  Signal(controller.showVideoView, controller.isCallEstablished, controller.cameraFailed, controller.videoSendState).map {
    case (true, true, false, VideoSendState.SEND) => true
    case _                                        => false
  }.onUi { visible =>
    verbose(s"video view visible: $visible")
    roundedLayout.setVisible(visible)
  }

  LayoutInflater.from(context).inflate(R.layout.calling_header, this, true)

  controller.subtitleText.onUi(subtitleView.setText)

  controller.conversationName.onUi(nameView.setText)

  controller.cbrEnabled.map {
    case true => getString(R.string.audio_message__constant_bit_rate)
    case false => ""
  }.onUi(bitRateModeView.setText)

  def setPreview(view: TextureView) = ControlsFragment.addVideoViewToLayout(roundedLayout, view)
}
