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
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.{FrameLayout, ImageView, LinearLayout}
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.MessageContent
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{NoAutowiring, Signal, SourceSignal}
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.assets.ImageLayoutAssetPart
import com.waz.zclient.messages.{HighlightViewPart, MessageViewPart, MsgPart}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class ImagePartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with ImageLayoutAssetPart
    with HighlightViewPart
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Image

  private lazy val assets = inject[AssetsController]

  private val imageIcon = findById[View](R.id.image_icon)

  private val imageView = findById[ImageView](R.id.image)

  val noWifi: SourceSignal[Boolean] with NoAutowiring = Signal(false)

  (for {
    noW  <- noWifi
    hide <- hideContent
  } yield !hide && noW).on(Threading.Ui)(imageIcon.setVisible)

  onClicked { _ => message.head.map(assets.showSingleImage(_, this))(Threading.Ui) }

  message.map(_.assetId).onUi { aId =>
    verbose(l"message asset id => $aId")

    aId.foreach { a =>
      WireGlide(getContext)
        .load(a)
        .apply(new RequestOptions().fitCenter())
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(imageView)
    }
  }

  hideContent.onUi { hide => imageView.setVisible(!hide) }

  override def onInflated(): Unit = {}
}

class WifiWarningPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.WifiWarning

  inflate(R.layout.message_wifi_warning_content)

  //A little bit hacky - but we can safely rely on the fact there should be an ImagePartView for each WifiWarningPartView
  //def to ensure we only get the ImagePartView after the view is attached to the window (the parent will be null otherwise)
  def imagePart: Option[ImagePartView] = Option(getParent).map(_.asInstanceOf[ViewGroup]).flatMap { p =>
    (0 until p.getChildCount).map(p.getChildAt).collectFirst {
      case v: ImagePartView => v
    }
  }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
    this.setVisible(false) //setVisible(true) is called for all view parts shortly before setting...
  }

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    imagePart.foreach(_.noWifi.on(Threading.Ui)(this.setVisible))
  }
}


