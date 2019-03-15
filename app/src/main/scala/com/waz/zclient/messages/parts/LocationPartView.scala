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
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, ImageView, TextView}
import com.waz.model.Dim2
import com.waz.service.NetworkModeService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.messages.{ClickableViewPart, HighlightViewPart, MsgPart}
import com.waz.zclient.{R, ViewHelper}

class LocationPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with HighlightViewPart
    with ClickableViewPart
    with ViewHelper
    with EphemeralPartView
    with EphemeralIndicatorPartView {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui

  override val tpe = MsgPart.Location

  inflate(R.layout.message_location_content)

  val network = inject[NetworkModeService]
  val browser = inject[BrowserController]

  val imageContainer: View   = findById(R.id.fl__row_conversation__map_image_container)
  val imageView: ImageView = findById(R.id.location_image)
  val tvName: TextView  = findById(R.id.ttv__row_conversation_map_name)
  val pinView: TextView = findById(R.id.gtv__row_conversation__map_pin_glyph)
  val placeholder: View = findById(R.id.ttv__row_conversation_map_image_placeholder_text)

  private val imageSize = Signal[Dim2]()

  val name = message.map(_.location.fold("")(_.getName))

  registerEphemeral(tvName)

  name { tvName.setText }

  accentController.accentColor.map(_.color) (pinView.setTextColor)

  onClicked { _ =>
    expired.head foreach {
      case true => // ignore click on expired msg
      case false => message.currentValue.flatMap(_.location) foreach { browser.openLocation }
    }
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    imageSize ! Dim2(imageContainer.getWidth, imageContainer.getHeight)
  }

  override def onDraw(canvas: Canvas): Unit = {
    super.onDraw(canvas)
  }
}
