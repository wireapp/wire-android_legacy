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
package com.waz.zclient.messages.parts.assets

import android.graphics.drawable.Drawable
import android.view.View.OnLayoutChangeListener
import android.view.{View, ViewGroup}
import android.widget.{FrameLayout, TextView}
import com.waz.model.{AssetData, Dim2, MessageContent}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageController.WireImage
import com.waz.zclient.messages.ClickableViewPart
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.assets.DeliveryState.{Downloading, OtherUploading}
import com.waz.zclient.messages.parts.{EphemeralIndicatorPartView, EphemeralPartView, ImagePartView}
import com.waz.zclient.utils.{StringUtils, _}
import com.waz.zclient.{R, ViewHelper}

trait AssetPart extends View with ClickableViewPart with ViewHelper with EphemeralPartView { self =>
  val controller = inject[AssetsController]

  def layoutList: PartialFunction[AssetPart, Int] = {
      case _: AudioAssetPartView => R.layout.message_audio_asset_content
      case _: FileAssetPartView  => R.layout.message_file_asset_content
      case _: ImagePartView      => R.layout.message_image_content
      case _: VideoAssetPartView => R.layout.message_video_asset_content
  }

  inflate(layoutList.orElse[AssetPart, Int]{
    case _ => throw new Exception("Unexpected AssetPart view type - ensure you define the content layout and an id for the content for the part")
  }(self))

  val asset = controller.assetSignal(message)
  val deliveryState = DeliveryState(message, asset)
  val completed = deliveryState.map(_ == DeliveryState.Complete)
  val accentColorController = inject[AccentColorController]
  protected val showDots: Signal[Boolean] = deliveryState.map(state => state == OtherUploading)

  lazy val assetBackground = new AssetBackground(showDots, expired, accentColorController.accentColor)

  //toggle content visibility to show only progress dot background if other side is uploading asset
  val hideContent = for {
    exp <- expired
    st <- deliveryState
  } yield exp || st == OtherUploading

  onInflated()

  def onInflated(): Unit
}

trait ActionableAssetPart extends AssetPart {
  protected val assetActionButton: AssetActionButton = findById(R.id.action_button)

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
    assetActionButton.message.publish(msg.message, Threading.Ui)
  }
}

trait PlayableAsset extends ActionableAssetPart {
  val duration = asset.map(_._1).map {
    case AssetData.WithDuration(d) => Some(d)
    case _ => None
  }
  val formattedDuration = duration.map(_.fold("")(d => StringUtils.formatTimeSeconds(d.getSeconds)))

  protected val durationView: TextView = findById(R.id.duration)

  formattedDuration.on(Threading.Ui)(durationView.setText)
}

trait FileLayoutAssetPart extends AssetPart with EphemeralIndicatorPartView {
  private lazy val content: ViewGroup = findById[ViewGroup](R.id.content)
  //For file and audio assets - we can hide the whole content
  //For images and video, we don't want the view to collapse (since they use merge tags), so we let them hide their content separately

  override def onInflated(): Unit = {
    content.setBackground(assetBackground)
    hideContent.map(!_).on(Threading.Ui) { v =>
      (0 until content.getChildCount).foreach(content.getChildAt(_).setVisible(v))
    }
  }
}

trait ImageLayoutAssetPart extends AssetPart with EphemeralIndicatorPartView {
  import ImageLayoutAssetPart._

  protected val imageDim = message.map(_.imageDimensions).collect { case Some(d) => d}
  protected val maxWidth = Signal[Int]()
  protected val maxHeight = Signal[Int]()
  override protected val showDots = deliveryState.map(state => state == OtherUploading || state == Downloading)

  val forceDownload = this match {
    case _: ImagePartView => false
    case _ => true
  }

  val imageDrawable = new ImageAssetDrawable(message map { m => WireImage(m.assetId) }, forceDownload = forceDownload)

  private lazy val imageContainer = returning(findById[FrameLayout](R.id.image_container)) {
    _.addOnLayoutChangeListener(new OnLayoutChangeListener {
      override def onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int): Unit =
        maxWidth ! v.getWidth
    })
  }

  hideContent.flatMap {
    case true => Signal.const[Drawable](assetBackground)
    case _ => imageDrawable.state map {
      case ImageAssetDrawable.State.Failed(_, _) |
           ImageAssetDrawable.State.Loading(_) => assetBackground
      case _ => imageDrawable
    } orElse Signal.const[Drawable](imageDrawable)
  }.on(Threading.Ui)(imageContainer.setBackground(_))

  val displaySize = for {
    maxW <- maxWidth
    maxH <- maxHeight
    Dim2(imW, imH) <- imageDim
  } yield {
    val heightToWidth = imH.toDouble / imW.toDouble

    val height = heightToWidth * maxW

    //fit image within view port height-wise (plus the little bit of buffer space), if it's height to width ratio is not too big. For super tall/thin
    //images, we leave them as is otherwise they might become too skinny to be viewed properly
    val scaleDownToHeight = maxH * (1 - scaleDownBuffer)
    val scaleDown = if (height > scaleDownToHeight && heightToWidth < scaleDownUnderRatio) scaleDownToHeight.toDouble / height.toDouble else 1D

    val scaledWidth = maxW * scaleDown

    //finally, make sure the width of the now height-adjusted image is either the full view port width, or less than
    //or equal to the centered area (taking left and right margins into consideration). This is important to get the
    //padding right in the next signal
    val finalWidth =
      if (scaledWidth <= maxW) scaledWidth
      else maxW

    val finalHeight = heightToWidth * finalWidth

    Dim2(finalWidth.toInt, finalHeight.toInt)
  }

  displaySize.onUi{ ds =>
    setLayoutParams(returning(getLayoutParams)(_.height = ds.height))
  }

  val padding = for {
    maxW <- maxWidth
    Dim2(dW, _) <- displaySize
  } yield
    if (dW >= maxW) Offset.Empty
    else Offset(0, 0, maxW - dW, 0)

  padding.onUi { p =>
    imageDrawable.padding ! p
    assetBackground.padding ! p
  }


  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
    opts.foreach { o =>
      maxHeight ! o.listDimensions.height
    }
  }
}

object ImageLayoutAssetPart {
  //a little bit of space for scaling images within the viewport
  val scaleDownBuffer = 0.05

  //Height to width - images with a lower ratio will be scaled to fit in the view port. Taller images will be allowed to keep their size
  val scaleDownUnderRatio = 2.0
}
