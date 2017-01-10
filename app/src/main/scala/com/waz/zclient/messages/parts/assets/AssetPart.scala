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
package com.waz.zclient.messages.parts.assets

import android.view.View
import android.widget.TextView
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{AssetData, Dim2, MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.controllers.AssetsController
import com.waz.zclient.messages.ClickableViewPart
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.assets.DeliveryState.OtherUploading
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{StringUtils, _}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageController.WireImage
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.Duration

trait AssetPart extends View with ClickableViewPart with ViewHelper {
  val controller = inject[AssetsController]

  def contentLayoutId: Int
  inflate(contentLayoutId)

  private lazy val content: View = findById[View](R.id.content)

  val asset = controller.assetSignal(message)
  val deliveryState = DeliveryState(message, asset)
  val completed = deliveryState.map(_ == DeliveryState.Complete)

  val assetBackground = new AssetBackground(deliveryState.map(_ == OtherUploading))
  setBackground(assetBackground)

  //toggle content visibility to show only progress dot background if other side is uploading asset
  deliveryState.map {
    case OtherUploading => false
    case _ => true
  }.on(Threading.Ui)(content.setVisible)
}

trait ActionableAssetPart extends AssetPart {
  protected val assetActionButton: AssetActionButton = findById(R.id.action_button)

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: MsgBindOptions): Unit = {
    super.set(msg, part, opts)
    assetActionButton.message.publish(msg.message, Threading.Ui)
  }
}

trait PlayableAsset extends ActionableAssetPart {
  val duration = asset.map(_._1).map {
    case AssetData.WithDuration(d) => d
    case _ => Duration.ZERO
  }
  val formattedDuration = duration.map(d => StringUtils.formatTimeSeconds(d.getSeconds))

  protected val durationView: TextView = findById(R.id.duration)

  //TODO there is more logic for what text to display in video views, but it doesn't seem to be used - confirm
  formattedDuration.on(Threading.Ui)(durationView.setText)

}


trait ImageLayoutAssetPart extends AssetPart {
  import ImageLayoutAssetPart._

  protected val imageDim = message map { _.imageDimensions.getOrElse(Dim2(1, 1)) }
  protected val maxWidth = Signal[Int]()
  protected val maxHeight = Signal[Int]()

  private lazy val contentPaddingStart = getDimenPx(R.dimen.content__padding_left)
  private lazy val contentPaddingEnd = getDimenPx(R.dimen.content__padding_right)

  val imageDrawable = new ImageAssetDrawable(message map { m => WireImage(m.assetId) })

  val displaySize = for {
    maxW <- maxWidth
    maxH <- maxHeight
    Dim2(imW, imH) <- imageDim
  } yield {
    val centered = maxW - contentPaddingStart - contentPaddingEnd

    val heightToWidth = imH.toDouble / imW.toDouble

    val width = if (imH > imW) centered else maxW
    val height = heightToWidth * width

    //fit image within view port height-wise (plus the little bit of buffer space), if it's height to width ratio is not too big. For super tall/thin
    //images, we leave them as is otherwise they might become too skinny to be viewed properly
    val scaleDownToHeight = maxH * (1 - scaleDownBuffer)
    val scaleDown = if (height > scaleDownToHeight && heightToWidth < scaleDownUnderRatio) scaleDownToHeight.toDouble / height.toDouble else 1D

    val scaledWidth = width * scaleDown

    //finally, make sure the width of the now height-adjusted image is either the full view port width, or less than
    //or equal to the centered area (taking left and right margins into consideration). This is important to get the
    //padding right in the next signal
    val finalWidth =
      if (scaledWidth <= centered) scaledWidth
      else if (scaledWidth >= maxW) maxW
      else centered

    val finalHeight = heightToWidth * finalWidth

    Dim2(finalWidth.toInt, finalHeight.toInt)
  }

  val padding = for {
    maxW <- maxWidth
    Dim2(dW, dH) <- displaySize
  } yield {
    if (dW >= maxW) Offset.Empty
    else {
      val left = if (getLayoutDirection == View.LAYOUT_DIRECTION_LTR) contentPaddingStart else maxW - contentPaddingStart - dW
      Offset(left, 0, maxW - dW - left, 0)
    }
  }

  padding { imageDrawable.padding ! _ }

  displaySize.map(_.height) { h =>
    setLayoutParams(returning(getLayoutParams)(_.height = h))
  }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: MsgBindOptions): Unit = {
    super.set(msg, part, opts)
    maxWidth.mutateOrDefault(identity, opts.listDimensions.width)
    maxHeight ! opts.listDimensions.height
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)
    maxWidth ! (right - left)
  }
}

object ImageLayoutAssetPart {
  //a little bit of space for scaling images within the viewport
  val scaleDownBuffer = 0.05

  //Height to width - images with a lower ratio will be scaled to fit in the view port. Taller images will be allowed to keep their size
  val scaleDownUnderRatio = 2.0
}
