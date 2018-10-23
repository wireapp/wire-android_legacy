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
package com.waz.zclient.messages.parts.quotes

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, TextView}
import com.waz.model.{AssetId, Dim2}
import com.waz.service.NetworkModeService
import com.waz.service.media.GoogleMapsMediaService
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.State
import com.waz.zclient.common.views.ImageController.{DataImage, ImageSource, WireImage}
import com.waz.zclient.messages.parts.QuoteView
import com.waz.zclient.utils.ContextUtils.getColor
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

class LocationQuoteView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper with QuoteView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

//  inflate(R.layout.message_location_quote)

  val accentController = inject[AccentColorController]
  val network          = inject[NetworkModeService]
  val browser          = inject[BrowserController]

  val imageView: View   = findById(R.id.fl__row_conversation__map_image_container)
  val tvName: TextView  = findById(R.id.ttv__row_conversation_map_name)
  val pinView: TextView = findById(R.id.gtv__row_conversation__map_pin_glyph)
  val placeholder: View = findById(R.id.ttv__row_conversation_map_image_placeholder_text)

  private val imageSize = Signal[Dim2]()

  val name = message.map(_.location.fold("")(_.getName))
  val image = for {
    msg <- message
    dim <- imageSize if dim.width > 0
  } yield
    msg.location.fold2[ImageSource](WireImage(msg.assetId), { loc =>
      DataImage(GoogleMapsMediaService.mapImageAsset(AssetId(s"${msg.assetId.str}_${dim.width}_${dim.height}"), loc, dim)) // use dimensions in id, to avoid caching images with different sizes
    })

  val imageDrawable = new ImageAssetDrawable(image, background = Some(new ColorDrawable(getColor(R.color.light_graphite_24))))

  val loadingFailed = imageDrawable.state.map {
    case State.Failed(_, _) => true
    case _ => false
  } .orElse(Signal const false)

  val showPin = imageDrawable.state.map {
    case State.Loaded(_, _, _) => true
    case _ => false
  }.orElse(Signal const false)

  name.onUi(tvName.setText)
  showPin.onUi(pinView.setVisible)

  accentController.accentColor.map(_.color) (pinView.setTextColor)

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    imageSize ! Dim2(imageView.getWidth, imageView.getHeight)
  }

  override def onDraw(canvas: Canvas): Unit = {
    super.onDraw(canvas)
  }
}
