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
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.assets.ImageLayoutAssetPart
import com.waz.zclient.messages.{HighlightViewPart, MessageViewPart, MsgPart}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}
import com.waz.threading.Threading._
import com.waz.zclient.messages.parts.assets.AssetPart.AssetPartViewState

class ImagePartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with ImageLayoutAssetPart
    with HighlightViewPart
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Image

  private lazy val assets = inject[AssetsController]

  private val imageView = findById[ImageView](R.id.image)
  private val obfuscationContainer = findById[View](R.id.obfuscation_container)
  private val restrictionContainer = findById[View](R.id.restriction_container)

  onClicked.onUi { _ =>
    viewState.head.foreach { state =>
      if (state != AssetPartViewState.Restricted) {
        message.head.map(assets.showSingleImage(_, this))(Threading.Ui)
      }
    }(Threading.Ui)
  }

  (for {
    msg <- message
    state <- viewState
  } yield (msg.assetId, state)).onUi { assetIdAndState =>
    val (aId, state) = assetIdAndState
    verbose(l"message asset id => $aId")
    
    if (state != AssetPartViewState.Restricted) {
      aId.foreach { a =>
        WireGlide(getContext)
          .load(a)
          .apply(new RequestOptions().fitCenter())
          .transition(DrawableTransitionOptions.withCrossFade())
          .into(imageView)
      }
    }
  }

  viewState.onUi {
    case AssetPartViewState.Restricted =>
      restrictionContainer.setVisible(true)
      obfuscationContainer.setVisible(false)
      imageView.setVisible(false)

    case AssetPartViewState.Obfuscated =>
      restrictionContainer.setVisible(false)
      obfuscationContainer.setVisible(true)
      imageView.setVisible(false)

    case AssetPartViewState.Loading =>
      restrictionContainer.setVisible(false)
      obfuscationContainer.setVisible(false)
      imageView.setVisible(false)

    case AssetPartViewState.Loaded =>
      restrictionContainer.setVisible(false)
      obfuscationContainer.setVisible(false)
      imageView.setVisible(true)

    case unknown =>
      info(l"Unknown AssetPartViewState: $unknown")
  }

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

}


