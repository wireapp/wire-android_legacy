/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.profile.preferences.views

import java.util.Locale

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import com.waz.model.otr.Client
import com.waz.zclient.R
import com.waz.zclient.ui.utils.TextViewUtils

class DeviceButton(context: Context, attrs: AttributeSet, style: Int) extends TextButton(context, attrs, style) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  subtitle.foreach(_.setTextColor(Color.WHITE))

  def setDevice(client: Client, self: Boolean): Unit = {
    title.foreach(_.setText(client.model))
    subtitle.foreach(setOptionText(_, Some(displayId(client))))
    subtitle.foreach(TextViewUtils.boldText)
    iconEnd.foreach(setOptionDrawable(_, drawableForClient(client, self)))
  }

  private def drawableForClient(client: Client, self: Boolean): Option[Drawable] = {
    if (self)
      None
    else if (client.isVerified)
      Option(ContextCompat.getDrawable(context, R.drawable.shield_full))
    else
      Option(ContextCompat.getDrawable(context, R.drawable.shield_half))
  }

  private def displayId(client: Client): String = {
    f"${client.id.str.toUpperCase(Locale.ENGLISH)}%16s" replace (' ', '0') grouped 4 map { group =>
      val (bold, normal) = group.splitAt(2)
      s"[[$bold]] $normal"
    } mkString " "
  }
}
