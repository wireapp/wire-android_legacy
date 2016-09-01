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
import android.graphics.{Color, PorterDuff}
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.{LinearLayout, RelativeLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.{Message, NetworkMode}
import com.waz.model.messages.media.MediaAssetData
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.NetworkModeService
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.zclient.controllers.BrowserController
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.State
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.{R, ViewHelper}

class YouTubePartView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.YouTube

  inflate(R.layout.message_youtube_content)

  val network = inject[NetworkModeService]
  val browser = inject[BrowserController]

  val tvTitle: TextView         = findById(R.id.ttv__youtube_message__title)
  val error: View               = findById(R.id.ttv__youtube_message__error)
  val glyphView: GlyphTextView  = findById(R.id.gtv__youtube_message__play)

  val alphaOverlay = getResourceFloat(R.dimen.content__youtube__alpha_overlay)

  private val content = Signal[MessageContent]()
  private val width = Signal[Int]()

  val media = content map { _.richMedia.getOrElse(MediaAssetData.empty(Message.Part.Type.YOUTUBE)) }
  val image = media.flatMap { _.artwork.fold2(Signal.empty[ImageSource], id => Signal.const[ImageSource](WireImage(id))) }
  val imageDrawable = new ImageAssetDrawable(image, background = Some(new ColorDrawable(getColor(R.color.content__youtube__background_color))))

  val loadingFailed = imageDrawable.state.map {
    case State.Failed(_) => true
    case _ => false
  }

  val showError = loadingFailed.zip(network.networkMode).map { case (failed, mode) => failed && mode != NetworkMode.OFFLINE }

  val title = for {
    m <- media
    failed <- loadingFailed
  } yield if (failed) "" else m.title

  val height = width map { _ * 9 / 16 }

  imageDrawable.setColorFilter(ColorUtils.injectAlpha(alphaOverlay, Color.BLACK), PorterDuff.Mode.DARKEN)
  setBackground(imageDrawable)

  title { tvTitle.setText }

  showError.on(Threading.Ui) { error.setVisible }

  loadingFailed { failed =>
    glyphView.setText(getString(if (failed) R.string.glyph__movie else R.string.glyph__play))
    glyphView.setTextColor(getColor(if (failed) R.color.content__youtube__error_indicator__color else R.color.content__youtube__text__color))
  }

  height { h =>
    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
  }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    width.mutateOrDefault(identity, widthHint)
    part foreach { content ! _ }
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    width ! (right - left)
  }

  this.onClick {
    content.currentValue foreach { c =>
      browser.openUrl(c.contentAsUri)
    }
  }
}
