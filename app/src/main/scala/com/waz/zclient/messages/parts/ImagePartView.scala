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
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.LinearLayout
import com.waz.model.{Dim2, MessageContent, MessageData}
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageController.WireImage

class ImagePartView(context: Context, attrs: AttributeSet, style: Int) extends View(context, attrs, style) with ImageLayoutAssetPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Image

  setBackground(imageDrawable) //FIXME, sets twice, kinda unnecessary
}
