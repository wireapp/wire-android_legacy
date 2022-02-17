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
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.threading.Threading._
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.participants.ParticipantsController.ClassifiedConversation
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getColor, getString}
import com.waz.zclient.{R, ViewHelper}
import com.wire.signals.Signal
import com.waz.zclient.utils._

class CallingHeader(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) =  this(context, null)

  private val controller  = inject[CallController]

  private lazy val nameView        = findById[TextView](R.id.ttv__calling__header__name)
  private lazy val subtitleView    = findById[TextView](R.id.ttv__calling__header__subtitle)
  private lazy val bitRateModeView = findById[TextView](R.id.ttv__calling__header__bitrate)

  lazy val closeButton: GlyphButton = findById[GlyphButton](R.id.calling_header_close)

  inflate(R.layout.calling_header, this)

  controller.subtitleText.onUi(subtitleView.setText)
  controller.conversationName.onUi(nameView.setText(_))

  Signal.zip(controller.isCallEstablished, controller.isGroupCall, controller.cbrEnabled).map {
    case (true, false, Some(true)) => getString(R.string.audio_message_constant_bit_rate)
    case _ => ""
  }.onUi(bitRateModeView.setText)

  private lazy val classifiedBanner = findById[FrameLayout](R.id.call_classified_banner)
  controller.isCurrentConvClassified.onUi {
    case ClassifiedConversation.Classified =>
      classifiedBanner.setBackgroundColor(getColor(R.color.background_light))
      classifiedBanner.setVisible(true)
    case ClassifiedConversation.NotClassified =>
      classifiedBanner.setBackgroundColor(getColor(R.color.background_dark))
      classifiedBanner.setVisible(true)
    case ClassifiedConversation.None =>
      classifiedBanner.setVisible(false)
  }

  private lazy val classifiedBannerText = findById[TypefaceTextView](R.id.call_classified_banner_text)
  controller.isCurrentConvClassified.onUi {
    case ClassifiedConversation.Classified =>
      classifiedBannerText.setTransformedText(getString(R.string.conversation_is_classified))
      classifiedBannerText.setTextColor(getColor(R.color.background_dark))
      classifiedBannerText.setVisible(true)
    case ClassifiedConversation.NotClassified =>
      classifiedBannerText.setTransformedText(getString(R.string.conversation_is_unclassified))
      classifiedBannerText.setTextColor(getColor(R.color.background_light))
      classifiedBannerText.setVisible(true)
    case ClassifiedConversation.None =>
      classifiedBannerText.setVisible(false)
  }
}
