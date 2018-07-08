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
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.{R, ViewHelper}

class CallingHeader(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) =  this(context, null)

  private val controller = inject[CallController]

  private lazy val nameView        = findById[TextView](R.id.ttv__calling__header__name)
  private lazy val subtitleView    = findById[TextView](R.id.ttv__calling__header__subtitle)
  private lazy val bitRateModeView = findById[TextView](R.id.ttv__calling__header__bitrate)

  lazy val closeButton: GlyphButton = findById[GlyphButton](R.id.calling_header_close)

  inflate(R.layout.calling_header, this)

  controller.subtitleText.onUi(subtitleView.setText)
  controller.conversationName.onUi(nameView.setText)

  controller.cbrEnabled.map {
    case true  => getString(R.string.audio_message__constant_bit_rate)
    case false => ""
  }.onUi(bitRateModeView.setText)


}
