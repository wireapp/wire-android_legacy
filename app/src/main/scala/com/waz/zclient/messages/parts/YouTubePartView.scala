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
import android.widget.{ImageView, LinearLayout, RelativeLayout, TextView}
import com.bumptech.glide.request.RequestOptions
import com.waz.api.{Message, NetworkMode}
import com.waz.model.{GeneralAssetId, MessageContent}
import com.waz.model.messages.media.MediaAssetData
import com.waz.service.NetworkModeService
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.core.images.transformations.DarkenTransformation
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

class YouTubePartView(context: Context, attrs: AttributeSet, style: Int)
  extends RelativeLayout(context, attrs, style)
    with ClickableViewPart with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.YouTube

  inflate(R.layout.message_youtube_content)

  private lazy val network = inject[NetworkModeService]
  private lazy val browser = inject[BrowserController]

  private lazy val tvTitle: TextView         = findById(R.id.ttv__youtube_message__title)
  private lazy val error: View               = findById(R.id.ttv__youtube_message__error)
  private lazy val glyphView: GlyphTextView  = findById(R.id.gtv__youtube_message__play)
  private lazy val previewImage: ImageView   = findById(R.id.gtv__youtube_message__preview)

  private val alphaOverlay = getResourceFloat(R.dimen.content__youtube__alpha_overlay)

  private val content = Signal[MessageContent]()
  private val width = Signal[Int]()

  private lazy val media: Signal[MediaAssetData] = content.map(_.richMedia.getOrElse(MediaAssetData.empty(Message.Part.Type.YOUTUBE)))
  private lazy val image: Signal[GeneralAssetId] = media.map(_.artwork).collect{case Some(id) => id}

  val loadingFailed = Signal(false)

  image.onUi { id =>
    WireGlide(context)
      .load(id)
      .apply(new RequestOptions().transform(new DarkenTransformation((alphaOverlay * 255).toInt, 1)))
      .into(previewImage)
  }

  val showError = loadingFailed.zip(network.networkMode).map { case (failed, mode) => failed && mode != NetworkMode.OFFLINE }

  val title = for {
    m <- media
    failed <- loadingFailed
  } yield if (failed) "" else m.title

  val height = width map { _ * 9 / 16 }

  title { tvTitle.setText }

  showError.on(Threading.Ui) { error.setVisible }

  loadingFailed { failed =>
    glyphView.setText(getString(if (failed) R.string.glyph__movie else R.string.glyph__play))
    glyphView.setTextColor(getColor(if (failed) R.color.content__youtube__error_indicator else R.color.content__youtube__text))
  }

  height { h =>
    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
  }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
    width.mutateOrDefault(identity, opts.map(_.listDimensions.width).getOrElse(0))
    part foreach { content ! _ }
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    width ! (right - left)
  }

  onClicked { _ =>
    for {
      c <- content.currentValue
      m <- message.currentValue
    } {
      browser.onYoutubeLinkOpened ! m.id
      browser.openUrl(c.contentAsUri)
    }

  }
}
