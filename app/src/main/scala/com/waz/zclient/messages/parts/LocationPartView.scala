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
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.CardView
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.waz.api.NetworkMode
import com.waz.model.{AssetId, Dim2, MessageContent, MessageData}
import com.waz.service.NetworkModeService
import com.waz.service.media.GoogleMapsMediaService
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.zclient.controllers.BrowserController
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.State
import com.waz.zclient.views.ImageController.{DataImage, ImageSource, WireImage}
import com.waz.zclient.{R, ViewHelper}

class LocationPartView(context: Context, attrs: AttributeSet, style: Int) extends CardView(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.Location

  inflate(R.layout.message_location_content)

  val network = inject[NetworkModeService]
  val browser = inject[BrowserController]
  val accent = inject[AccentColorController]

  val imageView: View   = findById(R.id.fl__row_conversation__map_image_container)
  val tvName: TextView  = findById(R.id.ttv__row_conversation_map_name)
  val pinView: TextView = findById(R.id.gtv__row_conversation__map_pin_glyph)
  val error: View       = findById(R.id.ttv__row_conversation_map_image_placeholder_text)

  private val imageSize = Signal[Dim2]()
  private val message = Signal[MessageData]()

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
    case State.Failed(_) => true
    case _ => false
  }

  val imageLoaded = imageDrawable.state.map {
    case State.Loaded(_, _) => true
    case _ => false
  }

  val showError = loadingFailed.zip(network.networkMode).map { case (failed, mode) => failed && mode != NetworkMode.OFFLINE }

  imageView.setBackground(imageDrawable)

  name { tvName.setText }
  imageLoaded.on(Threading.Ui) { pinView.setVisible }
  showError.on(Threading.Ui) { error.setVisible }

  accent.accentColor { c => pinView.setTextColor(c.getColor()) }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    message ! msg
  }

  this.onClick {
    message.currentValue.flatMap(_.location) foreach { browser.openLocation }
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    imageSize ! Dim2(imageView.getWidth, imageView.getHeight)
  }
}
