/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
import android.graphics.{Color, PorterDuff}
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.{ImageView, RelativeLayout, TextView}
import com.waz.api.Message
import com.waz.model.MessageContent
import com.waz.model.messages.media.{ArtistData, MediaAssetData}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.messages.ClickableViewPart
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ContextUtils.{getColor, getResourceFloat, getString}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.State
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}
import com.waz.utils._
import com.waz.zclient.utils._
import com.waz.zclient.controllers.BrowserController
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.Message.Part

/**
  * Created by admin on 06/03/17.
  */
abstract class SoundPreviewPartView(context: Context, attrs: AttributeSet, style: Int, messagePartType: Message.Part.Type)
  extends RelativeLayout(context, attrs, style) with ClickableViewPart with ViewHelper {

  def getImageView: ImageView
  def getTitleView: TextView
  def getArtistView: TextView
  def getErrorView: GlyphTextView

  //val network = inject[NetworkModeService] // currently not used, but network.networkMode might be useful
  val browser = inject[BrowserController]

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: MsgBindOptions): Unit = {
    super.set(msg, part, opts)
    part foreach { content ! _ }
  }

  onClicked { _ =>
    for {
      c <- content.currentValue
    } {
      browser.openUrl(c.contentAsUri)
    }
  }

  def init() = {
    getImageView.setVisible(true)
    getErrorView.setVisible(false)

    val imageDrawable = {
      val image = media.flatMap {
        _.artwork.fold2(Signal.empty[ImageSource], id => Signal.const[ImageSource](WireImage(id)))
      }
      val imageDrawable = new ImageAssetDrawable(image, background = Some(new ColorDrawable(getColor(R.color.content__youtube__background_color))))

      val alphaOverlay = getResourceFloat(R.dimen.content__youtube__alpha_overlay)
      imageDrawable.setColorFilter(ColorUtils.injectAlpha(alphaOverlay, Color.BLACK), PorterDuff.Mode.DARKEN)
      getImageView.setBackground(imageDrawable)
      imageDrawable
    }

    val loadingFailed = {
      val loadingFailed = imageDrawable.state.map {
        case State.Failed(_, _) => true
        case _ => false
      }

      loadingFailed { failed =>
        getErrorView.setVisible(failed)
        getErrorView.setText(getString(if (failed) R.string.glyph__movie else R.string.glyph__play))
        getErrorView.setTextColor(getColor(if (failed) R.color.content__youtube__error_indicator__color else R.color.content__youtube__text__color))
      }

      loadingFailed
    }

    val titleAndArtist = for {
      m <- media
      failed <- loadingFailed
    } yield if (failed) ("", "") else (m.title, m.artist.getOrElse(ArtistData("", None)).name)

    titleAndArtist { case (title, artist) =>
      getTitleView.setText(title)
      getArtistView.setText(artist)
    }

  }

  private val content = Signal[MessageContent]()
  private val media = content map { _.richMedia.getOrElse( MediaAssetData.empty(messagePartType) ) }
}
