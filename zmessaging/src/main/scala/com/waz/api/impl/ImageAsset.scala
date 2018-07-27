/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.api.impl

import android.graphics.Bitmap
import android.media.ExifInterface
import android.os.Parcel
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.ImageAsset.{BitmapLoadHandle, Parcelable}
import com.waz.api.{BitmapCallback, LoadHandle}
import com.waz.bitmap.BitmapUtils
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.{BitmapLoaded, LoadingFailed}
import com.waz.service.images.{BitmapSignal, ImageLoader}
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.ui.MemoryImageCache.BitmapRequest.Single
import com.waz.ui._
import com.waz.utils._
import com.waz.utils.events.Signal

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.ref.WeakReference

class ImageAsset(val id: AssetId)(implicit ui: UiModule) extends com.waz.api.ImageAsset with UiFlags with BitmapLoading with UiObservable with SignalLoading {

  var data = AssetData.Empty

  addLoader(zms => zms.assetsStorage.signal(id).flatMap {
    case a@AssetData.IsVideo() => a.previewId.map(zms.assetsStorage.signal).getOrElse(Signal.const(AssetData.Empty))
    case a => Signal.const(a)
  }) { im =>
    if (this.data != im) {
      this.data = im
      notifyChanged()
    }
  }

  protected def getBitmap(req: BitmapRequest, callback: BitmapCallback): LoadHandle =
    BitmapLoadHandle({ BitmapSignal(_, data, req) }, callback)

  override def equals(other: Any): Boolean = other match {
    case other: ImageAsset => id == other.id
    case _ => false
  }

  override def hashCode: Int = id.hashCode

  override def writeToParcel(dest: Parcel, flags: Int): Unit = {
    dest.writeInt(Parcelable.FlagWire)
    dest.writeString(id.str)
  }

  override def toString = s"ImageAsset($data)"
}

class LocalImageAsset(img: AssetData)(implicit ui: UiModule) extends ImageAsset(img.id) with DisableSignalLoading {
  data = img

  override def addLoader[A, B <: A](signal: (ZMessaging) => Signal[B], defaultValue: A)(onLoaded: (A) => Unit)(implicit ui: UiModule) = null

  override def getBitmap(req: BitmapRequest, callback: BitmapCallback): LoadHandle = {
    new BitmapLoadHandle({
      case None => BitmapSignal(data, req, ui.globalImageLoader, ui.network)
      case Some(zms) => BitmapSignal(zms, data, req)
    }, callback)
  }

  override def writeToParcel(p: Parcel, flags: Int): Unit = {
    p.writeInt(Parcelable.FlagLocal)
    p.writeString(JsonEncoder.encodeString(img))
  }

  override def toString = s"LocalImageAsset($data)"
}

//used for temporary asset data where the medium might be very big (i.e. Giphy). We don't want to always trigger downloading of the medium
//if the user won't look at it.
class LocalImageAssetWithPreview(preview: Option[AssetData], medium: AssetData)(implicit ui: UiModule) extends LocalImageAsset(medium) {

  override def getBitmap(req: BitmapRequest, callback: BitmapCallback): LoadHandle = req match {
      case Single(_, _) => new BitmapLoadHandle ({
        case Some(zms) => BitmapSignal(zms, preview.getOrElse(medium), req)
        case _ => BitmapSignal(preview.getOrElse(medium), req, ui.globalImageLoader, ui.network)
      }, callback)
      case _ => super.getBitmap(req, callback)
    }
  override def writeToParcel(p: Parcel, flags: Int): Unit = {
    p.writeInt(Parcelable.FlagLocalWithPreview)
    p.writeString(JsonEncoder.encodeString(medium))
    preview.foreach(v => p.writeString(JsonEncoder.encodeString(v)))
  }
}

class LocalBitmapAsset(bitmap: Bitmap, orientation: Int = ExifInterface.ORIENTATION_NORMAL)(implicit ui: UiModule) extends ImageAsset(AssetId()) {

  verbose(s"creating asset from bitmap, orientation: $orientation")

  val mime   = Mime(BitmapUtils.getMime(bitmap))
  val (w, h) = if (ImageLoader.Metadata.shouldSwapDimens(orientation)) (bitmap.getHeight, bitmap.getWidth) else (bitmap.getWidth, bitmap.getHeight)
  val imageData = ui.assetLoader.loadFromBitmap(data.id, bitmap, orientation)

  data = AssetData(
    id,
    mime = mime,
    metaData = Some(AssetMetaData.Image(Dim2(w, h), Medium)),
    data = {
      verbose(s"data requested, compress completed: ${imageData.isCompleted}")
      // XXX: this is ugly, but will only be accessed from bg thread and very rarely, so we should be fine with that hack
      LoggedTry(Await.result(imageData, 15.seconds)).toOption
    }
  )

  override def getBitmap(req: BitmapRequest, callback: BitmapCallback): LoadHandle = {
    verbose(s"get bitmap")
    new BitmapLoadHandle(_ => Signal.future(imageData) flatMap { _ => BitmapSignal(data, req, ui.globalImageLoader, ui.network) }, callback)
  }

  override def writeToParcel(p: Parcel, flags: Int): Unit = {
    p.writeInt(Parcelable.FlagLocal)
    p.writeString(JsonEncoder.encodeString(data))
  }
}

object ImageAsset {

  object Parcelable {
    val FlagEmpty = 0
    val FlagLocal = 1
    val FlagWire  = 2
    val FlagLocalWithPreview  = 3
  }

  object Empty extends com.waz.api.ImageAsset with UiFlags with UiObservable {
    override def getSingleBitmap(width: Int, callback: BitmapCallback): LoadHandle = EmptyLoadHandle
    override def writeToParcel(dest: Parcel, flags: Int): Unit = dest.writeInt(Parcelable.FlagEmpty)
  }

  class BitmapLoadHandle(signal: Option[ZMessaging] => Signal[BitmapResult], callback: BitmapCallback)(implicit ui: UiModule) extends LoadHandle with SignalLoading {
    private var prev = Option.empty[(WeakReference[Bitmap], Int)]

    private var loader = Option(addLoaderOpt(signal) {
      case res@BitmapLoaded(bitmap, etag) =>
        if (prev.forall(p => !p._1.get.contains(bitmap) || p._2 != etag)) {
          // make sure that callback is not called twice with the same image, UI doesn't like that
          prev = Some((new WeakReference(bitmap), etag))
          callback.onBitmapLoaded(bitmap)
        }
      case LoadingFailed(ex) =>
        warn(s"bitmap loading failed", ex)
        callback.onBitmapLoadingFailed()
      case BitmapResult.Empty =>
        verbose(s"ignoring empty bitmap")
    })

    override def cancel(): Unit = {
      loader.foreach(_.destroy())
      loader = None
      prev = None
    }
  }

  object BitmapLoadHandle {
    def apply(signal: ZMessaging => Signal[BitmapResult], callback: BitmapCallback)(implicit ui: UiModule) =
      new BitmapLoadHandle(_.fold2(Signal.const(BitmapResult.Empty), signal), callback)
  }

}

trait UiFlags {
  var mirrored = false

  def setMirrored(mirrored: Boolean): Unit = this.mirrored = mirrored
}

trait BitmapLoading {
  self: com.waz.api.ImageAsset with UiFlags =>

  protected def getBitmap(req: BitmapRequest, callback: BitmapCallback): LoadHandle

  override def getSingleBitmap(width: Int, callback: BitmapCallback): LoadHandle = getBitmap(Single(width, mirror = this.mirrored), callback)
}

