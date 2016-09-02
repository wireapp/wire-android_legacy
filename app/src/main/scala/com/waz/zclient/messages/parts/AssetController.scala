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

import android.view.{View, ViewGroup}
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api
import com.waz.api.AssetStatus._
import com.waz.api.Message
import com.waz.model.AssetMetaData.HasDuration
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, Content, UnauthenticatedContent}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient._
import com.waz.zclient.messages.MessageViewPart
import com.waz.zclient.messages.parts.DeliveryState.{Complete, OtherUploading}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, StringUtils}
import com.waz.zclient.views.ImageController.WireImage
import com.waz.zclient.views.{ImageAssetDrawable, AssetActionButtonNew, AssetBackground}
import org.threeten.bp.Duration

import scala.PartialFunction._

class AssetController(implicit inj: Injector) extends Injectable {

  val zms = inject[Signal[ZMessaging]]
  val assets = zms.map(_.assets)
  val messages = zms.map(_.messages)

  def assetSignal(mes: Signal[MessageData]) = mes.flatMap(m => assets.flatMap(_.assetSignal(m.assetId)))

  def downloadProgress(id: AssetId) = assets.flatMap(_.downloadProgress(id))

  def uploadProgress(id: AssetId) = assets.flatMap(_.uploadProgress(id))

  def cancelUpload(m: MessageData) = assets.currentValue.foreach(_.cancelUpload(m.assetId, m.id))

  def cancelDownload(m: MessageData) = assets.currentValue.foreach(_.cancelDownload(m.assetId))

  def retry(m: MessageData) = if (m.state == Message.Status.FAILED || m.state == Message.Status.FAILED_READ) messages.currentValue.foreach(_.retryMessageSending(m.convId, m.id))

  def getPlaybackControls(asset: Signal[AnyAssetData]): Signal[PlaybackControls] = asset.flatMap { a =>
    if (cond(a.mimeType.orDefault) { case Mime.Audio() => true }) Signal.const(new PlaybackControls(a.id))
    else Signal.empty[PlaybackControls]
  }

  //TODO is this okay here as an inner class?
  class PlaybackControls(assetId: AssetId) {
    val rAndP = zms.map(_.global.recordingAndPlayback)

    val isPlaying = rAndP.flatMap(rP => rP.isPlaying(AssetMediaKey(assetId)))
    val playHead = rAndP.flatMap(rP => rP.playhead(AssetMediaKey(assetId)))

    private def rPAction(f: (GlobalRecordAndPlayService, AssetMediaKey, Content, Boolean) => Unit): Unit = {
      for {
        as <- assets.currentValue
        rP <- rAndP.currentValue
        isPlaying <- isPlaying.currentValue
      } {
        as.getAssetUri(assetId).foreach {
          case Some(uri) => f(rP, AssetMediaKey(assetId), UnauthenticatedContent(uri), isPlaying)
          case None =>
        }(Threading.Background)
      }
    }

    def playOrPause() = rPAction { case (rP, key, content, playing) => if (playing) rP.pause(key) else rP.play(key, content) }

    def setPlayHead(duration: Duration) = rPAction { case (rP, key, content, playing) => rP.setPlayhead(key, content, duration) }
  }

}

trait AssetPart extends View with MessageViewPart with ViewHelper {
  val zms = inject[Signal[ZMessaging]]
  val assets = inject[AssetController]
  val message = Signal[MessageData]()
  val asset = assets.assetSignal(message)
  val assetId = asset.map(_._1.id)
  val deliveryState = DeliveryState(message, asset)
  val actionReady = deliveryState.map { case Complete => true; case _ => false }

  val progressDots = new AssetBackground(deliveryState.map { case OtherUploading => true; case _ => false })
  setBackground(progressDots)

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    message ! msg
  }

}

trait ContentAssetPart extends AssetPart {
  def inflate(): Unit

  inflate()
  private val content: View = findById(R.id.content)

  //toggle content visibility to show only progress dot background if other side is uploading asset
  deliveryState.map {
    case OtherUploading => false
    case _ => true
  }.on(Threading.Ui)(content.setVisible)
}

trait ImageLayoutAssetPart extends AssetPart {
  protected val imageDim = message map { _.imageDimensions.getOrElse(Dim2(1, 1)) }
  protected val width = Signal[Int]()

  val imageDrawable = new ImageAssetDrawable(message map { m => WireImage(m.assetId) })

  val height = for {
    w <- width
    Dim2(imW, imH) <- imageDim
  } yield imH * w / imW  // TODO: improve view size computation

  height { h =>
    val w = getLayoutParams.width
    val margin = if (h > w) getDimenPx(R.dimen.content__padding_left) else 0
    val displayWidth = w - 2 * margin
    val height = (h * (displayWidth.toDouble / w)).toInt
    //TODO, set the imageDrawable to draw only within the bounds of `displayWidth`
    val pms = new LinearLayout.LayoutParams(w, height)
    pms.setMargins(margin, 0, margin, 0)
    setLayoutParams(pms)
  }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    super.set(pos, msg, part, widthHint)
    width.mutateOrDefault(identity, widthHint)
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)
    width ! (right - left)
  }
}

trait ActionableAssetPart extends ContentAssetPart {
  protected val assetActionButton: AssetActionButtonNew = findById(R.id.action_button)

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    super.set(pos, msg, part, widthHint)
    assetActionButton.message ! msg
  }
}

trait PlayableAsset extends ActionableAssetPart {
  val duration = asset.map(_._1).map {
    case AnyAssetData(_, _, _, _, _, Some(HasDuration(d)), _, _, _, _, _) => d
    case _ => Duration.ZERO
  }
  val formattedDuration = duration.map(d => StringUtils.formatTimeSeconds(d.getSeconds))

  protected val durationView: TextView = findById(R.id.duration)

  //TODO there is more logic for what text to display in video views, but it doesn't seem to be used - confirm
  formattedDuration.on(Threading.Ui)(durationView.setText)

}

sealed trait DeliveryState

object DeliveryState {

  case object Complete extends DeliveryState

  case object OtherUploading extends DeliveryState

  case object Uploading extends DeliveryState

  case object Downloading extends DeliveryState

  case object Cancelled extends DeliveryState

  trait Failed extends DeliveryState

  case object UploadFailed extends Failed

  case object DownloadFailed extends Failed

  case object Unknown extends DeliveryState

  private def apply(as: api.AssetStatus, ms: Message.Status): DeliveryState = {
    val res = (as, ms) match {
      case (UPLOAD_CANCELLED, _) => Cancelled
      case (UPLOAD_FAILED, _) => UploadFailed
      case (DOWNLOAD_FAILED, _) => DownloadFailed
      case (UPLOAD_NOT_STARTED | META_DATA_SENT | PREVIEW_SENT | UPLOAD_IN_PROGRESS, mState) =>
        mState match {
          case Message.Status.FAILED => UploadFailed
          case Message.Status.SENT => OtherUploading
          case _ => Uploading
        }
      case (DOWNLOAD_IN_PROGRESS, _) => Downloading
      case (UPLOAD_DONE | DOWNLOAD_DONE, _) => Complete
      case _ => Unknown
    }
    verbose(s"Mapping Asset.Status: $as, and Message.Status $ms to DeliveryState: $res")
    res
  }

  def apply(message: Signal[MessageData], asset: Signal[(AnyAssetData, api.AssetStatus)]): Signal[DeliveryState] =
    message.zip(asset).map { case (m, (_, s)) => apply(s, m.state) }
}
