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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import com.waz.model.otr.Client
import com.waz.zclient.R
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils.{getDrawable, getString}
import com.waz.zclient.utils.RichClient
import com.waz.zclient.utils.Time.TimeStamp

class DeviceButton(context: Context, attrs: AttributeSet, style: Int) extends PictureTextButton(context, attrs, style) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  def setDevice(client: Client, self: Boolean): Unit = {
    title.foreach(_.setText(client.model))
    subtitle.foreach(setOptionText(_, Some(displayId(client))))
    subtitle.foreach(TextViewUtils.boldText)
    setDrawableEnd(drawableForClient(client, self))
  }

  private def drawableForClient(client: Client, self: Boolean): Option[Drawable] = {
    if (self)
      None
    else
      Option(getDrawable(if (client.isVerified) R.drawable.shield_full else R.drawable.shield_half))
  }

  private def displayId(client: Client): String = {
    val date = client.regTime match {
      case Some(regTime) =>
        getString(R.string.pref_devices_device_activation_subtitle, TimeStamp(regTime).string)
      case _ =>
        ""
    }
    s"ID: ${client.displayId}\n$date"
  }

}
