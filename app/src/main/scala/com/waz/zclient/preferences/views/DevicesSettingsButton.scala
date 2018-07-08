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
package com.waz.zclient.preferences.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.waz.zclient.R
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.RichView

class DevicesSettingsButton(context: Context, attrs: AttributeSet, style: Int) extends TextButton(context, attrs, style) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def layoutId = R.layout.preference_devices_button

  val countBadge = findById[FrameLayout](R.id.count_badge)
  val countText = findById[TypefaceTextView](R.id.count_text)

  def setCount(count: Int): Unit = {
    countText.setText(count)
    count match {
      case 0 =>
        countBadge.setVisible(false)
      case _ =>
        countBadge.setVisible(true)
    }
  }
}
