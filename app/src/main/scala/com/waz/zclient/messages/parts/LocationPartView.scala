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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, ImageView, TextView}
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.ImageViewTarget
import com.waz.api.MessageContent.Location
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.ProgressDotsDrawable
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.{ClickableViewPart, HighlightViewPart, MsgPart}
import com.waz.zclient.{R, ViewHelper}


class LocationPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with HighlightViewPart
    with ClickableViewPart
    with ViewHelper
    with EphemeralPartView
    with EphemeralIndicatorPartView
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui

  inflate(R.layout.message_location_content)

  override val tpe = MsgPart.Location

  private val name = message.map(_.location.fold("")(_.getName))
  private val location = message.map(_.location)

  private val imageView: ImageView = findById(R.id.location_image)
  private val textView: TextView  = findById(R.id.ttv__row_conversation_map_name)
  private val pinView: TextView = findById(R.id.gtv__row_conversation__map_pin_glyph)
  private val dotsDrawable = new ProgressDotsDrawable()

  private val browser = inject[BrowserController]

  setupTextView()
  setupPinView()
  setupImageView()
  setupOnClickHandler()

  private def setupTextView(): Unit = {
    registerEphemeral(textView)
    name { textView.setText }
  }

  private def setupPinView(): Unit = {
    accentController.accentColor.map(_.color) (pinView.setTextColor)
    pinView.setVisibility(View.VISIBLE)
  }

  private def setupImageView(): Unit = {
    location {
      case Some(loc) => loadMapPreview(loc)
      case None => warn(l"No location data.")
    }
  }

  private def loadMapPreview(location: Location): Unit = {
    val options = new RequestOptions()
      .centerCrop()
      .placeholder(dotsDrawable)

    val target = new ImageViewTarget[Drawable](imageView) {
      override def setResource(resource: Drawable): Unit = {
        registerEphemeral(imageView, resource)
      }
    }

    WireGlide(context)
      .load(location)
      .apply(options)
      .into(target)
  }

  private def setupOnClickHandler(): Unit = {
    onClicked { _ =>
      expired.head foreach {
        case true => // ignore click on expired msg
        case false => message.currentValue.flatMap(_.location) foreach { browser.openLocation }
      }
    }
  }
}
