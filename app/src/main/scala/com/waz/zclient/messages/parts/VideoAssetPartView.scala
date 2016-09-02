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
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.model.{Dim2, MessageContent, MessageData}
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.messages.MsgPart
import com.waz.zclient.messages.parts.DeliveryState.OtherUploading
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{AssetUtils, RichView}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.State.Loaded
import com.waz.zclient.views.ImageController.WireImage

class VideoAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with PlayableAsset {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.VideoAsset

  override def inflate() = inflate(R.layout.message_video_asset_content)

  private val durationView: TextView = findById(R.id.duration)
  inflate(R.layout.message_video_asset_content)

  private val imageDrawable = new ImageAssetDrawable(assetId.map(WireImage))

  imageDrawable.state.map {
    case Loaded(_, _) => getColor(R.color.white)
    case _ => getColor(R.color.black)
  }.on(Threading.Ui)(durationView.setTextColor)

  deliveryState.on(Threading.Ui) {
    case OtherUploading => setBackground(progressDots)
    case _ =>
      setBackground(imageDrawable)
      imageDrawable.state.on(Threading.Ui) {
        case ImageAssetDrawable.State.Failed(_) => setBackground(progressDots)
        case _ =>
      }
  }

  //TODO there is more logic for what text to display, but it doesn't seem to be used - confirm
  formattedDuration.on(Threading.Ui)(durationView.setText)

  actionReady.on(Threading.Ui) {
    case true =>
      assetActionButton.onClick {
        for {
          id <- assetId.currentValue
          as <- zms.map(_.assets).currentValue
          mime <- asset.map(_._1.mimeType).currentValue
        } {
          as.getAssetUri(id).foreach {
            case Some(uri) =>
              val intent = AssetUtils.getOpenFileIntent(uri, mime.orDefault.str)
              context.startActivity(intent)
            case _ =>
          }(Threading.Ui)
        }
      }
    case _ =>
  }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    super.set(pos, msg, part, widthHint)

    val Dim2(w, h) = msg.imageDimensions.getOrElse(Dim2(1, 1))
    val margin = if (h > w) getDimenPx(R.dimen.content__padding_left) else 0
    val displayWidth = widthHint - 2 * margin
    val height = (h * (displayWidth.toDouble / w)).toInt

    val pms = new LinearLayout.LayoutParams(displayWidth, height)
    pms.setMargins(margin, 0, margin, 0)
    setLayoutParams(pms)
  }
}
