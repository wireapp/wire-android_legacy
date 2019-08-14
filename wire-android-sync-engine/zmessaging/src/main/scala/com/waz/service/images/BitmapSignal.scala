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
package com.waz.service.images

import android.graphics.{Bitmap => ABitmap}
import com.waz.api.NetworkMode
import com.waz.bitmap
import com.waz.bitmap.BitmapUtils
import com.waz.bitmap.gif.{Gif, GifAnimator}
import com.waz.cache.LocalData
import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{AssetData, AssetId, Mime}
import com.waz.service.{DefaultNetworkModeService, NetworkModeService, ZMessaging}
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.{BitmapLoaded, LoadingFailed}
import com.waz.service.downloads.AssetLoader
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.Threading.Implicits.Background
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.ui.MemoryImageCache.BitmapRequest.{Blurred, Round, Single}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.{IoUtils, WeakMemCache}
import com.waz.utils.wrappers.{Bitmap, EmptyBitmap}

import scala.concurrent.Future

abstract class BitmapSignal(req: BitmapRequest, network: NetworkModeService, downloadImagesAlways: Signal[Boolean])
  extends Signal[BitmapResult]
    with DerivedLogTag { signal =>

  import BitmapSignal._

  private var future = CancellableFuture.successful[Unit](())

  private lazy val waitForWifi: Unit = network.networkMode.zip(downloadImagesAlways) {
    case (NetworkMode.WIFI, _) => restart()
    case (_, true) => restart()
    case _ =>
  }(EventContext.Global)

  override protected def onWire(): Unit = restart()

  protected def loader(req: BitmapRequest): Loader

  override protected def onUnwire(): Unit = future.cancel()

  private def load(loader: Loader) = {
    future = loader.loadCached().flatMap {
      case Some(data) => loader.process(data, signal)
      case None => loader.load().flatMap { loader.process(_, signal) } // will try with download
    }
    future onFailure {
      case _: CancelException =>
      case ex@AssetLoader.DownloadOnWifiOnlyException =>
        signal publish LoadingFailed(ex)
        waitForWifi
      case ex =>
        warn(l"bitmap loading failed", ex)
        signal publish LoadingFailed(ex)
    }
  }

  private def restart() =
    if (req.width > 0) load(loader(req))
    else warn(l"invalid bitmap request, width <= 0: $req") // ignore requests with invalid size

}

object BitmapSignal {
  type AssetStore = (AssetId) => Future[Option[AssetData]]
  val EmptyAssetStore = (_: AssetId) => Future.successful(Option.empty[AssetData])

  private[images] val signalCache = new WeakMemCache[Any, Signal[BitmapResult]]

  /**
    * @param forceDownload true for images that should be downloaded even when "DownloadImagesAlways" is set to false and the user is not on WIFI (true by default)
    */
  def apply(asset: AssetData,
            req: BitmapRequest,
            service: ImageLoader,
            network: DefaultNetworkModeService,
            assets: AssetStore = EmptyAssetStore,
            downloadImagesAlways: Signal[Boolean] = Signal.const(true),
            forceDownload: Boolean = true): Signal[BitmapResult] = {
    if (!asset.isImage) Signal(BitmapResult.Empty)
    else signalCache((asset, req), new AssetBitmapSignal(asset, req, service, network, assets, downloadImagesAlways, forceDownload))
  }

  def apply(zms: ZMessaging, asset: AssetData, req: BitmapRequest): Signal[BitmapResult] =
    apply(asset, req, zms.imageLoader, zms.network, zms.oldAssetStorage.get, zms.userPrefs.preference(UserPreferences.DownloadImagesAlways).signal)

  sealed trait Loader {
    type Data
    def loadCached(): CancellableFuture[Option[Data]]
    def load(): CancellableFuture[Data]
    def process(result: Data, signal: BitmapSignal): CancellableFuture[Unit]
  }

  object EmptyLoader extends Loader {
    override type Data = Bitmap
    override def loadCached() = CancellableFuture.successful(None)
    override def load() = CancellableFuture.successful(bitmap.EmptyBitmap)
    override def process(result: Bitmap, signal: BitmapSignal) = CancellableFuture.successful(())
  }

  class MimeCheckLoader(asset: AssetData, req: BitmapRequest, imageLoader: ImageLoader, assets: AssetStore, forceDownload: Boolean) extends Loader {
    override type Data = Either[Bitmap, Gif]

    lazy val gifLoader    = new GifLoader(asset, req, imageLoader, assets, forceDownload)
    lazy val bitmapLoader = new AssetBitmapLoader(asset, req, imageLoader, assets, forceDownload)

    def detectMime(data: LocalData) = Threading.IO {IoUtils.withResource(data.inputStream)(in => Mime(BitmapUtils.detectImageType(in)))}

    override def loadCached() = imageLoader.loadRawCachedData(asset).flatMap {
      case Some(data) => detectMime(data) flatMap {
        case Mime.Image.Gif => gifLoader.loadCached() map (_.map(Right(_)))
        case _ => bitmapLoader.loadCached() map (_.map(Left(_)))
      }
      case None => CancellableFuture.successful(None)
    }.recover {
      case e: Throwable => None
    }

    override def load(): CancellableFuture[Data] = imageLoader.loadRawImageData(asset, forceDownload) flatMap {
      case Some(data) => detectMime(data) flatMap {
        case Mime.Image.Gif => gifLoader.load() map (Right(_))
        case _ => bitmapLoader.load() map (Left(_))
      }
      case None => CancellableFuture.failed(new Exception(s"No data could be downloaded for $asset"))
    }

    override def process(result: Data, signal: BitmapSignal) = result match {
      case Right(gif) => gifLoader.process(gif, signal)
      case Left(bitmap) => bitmapLoader.process(bitmap, signal)
    }
  }

  abstract class BitmapLoader(req: BitmapRequest, imageLoader: ImageLoader) extends Loader {
    override type Data = Bitmap

    val imageCache = imageLoader.memoryCache

    def id: AssetId

    override def process(result: Bitmap, signal: BitmapSignal) = {
      def generateResult: CancellableFuture[Bitmap] = {
        if (result == EmptyBitmap) CancellableFuture.successful(result)
        else req match {
          case Round(width, borderWidth, borderColor) => //result will be the square bitmap loaded earlier
            withCache(width) {
              Threading.ImageDispatcher {BitmapUtils.createRoundBitmap(result, width, borderWidth, borderColor)}
            }
          case Blurred(width, blurRadius, blurPasses) =>
            withCache(width) {
              Threading.ImageDispatcher {BitmapUtils.createBlurredBitmap(result, width, blurRadius, blurPasses)}
            }
          case _ =>
            CancellableFuture.successful(result)
        }
      }

      def withCache(width: Int)(loader: => CancellableFuture[Bitmap]) = {
        imageCache.reserve(id, req, width * width * 2)
        imageCache(id, req, width, loader)
      }

      generateResult map {
        case EmptyBitmap => signal publish BitmapResult.Empty
        case bmp => signal publish BitmapLoaded(bmp)
      }
    }
  }

  class AssetBitmapLoader(asset: AssetData, req: BitmapRequest, imageLoader: ImageLoader, assets: AssetStore, forceDownload: Boolean) extends BitmapLoader(req, imageLoader) {
    override def id = asset.id

    def data = asset.previewId match {
      case Some(pId) if asset.width > req.width * 3 =>
        // asset is significantly bigger than requested image, let's check if preview is big enough
        // in case of chatheads we often need small images and it would be enough to only load smallProfile version
        CancellableFuture.lift(assets(pId)) map {
          case Some(d) if d.width > req.width * .85 => d
          case _ => asset
        }
      case _ => CancellableFuture successful asset
    }

    val initialReq = req match {
      case Round(w, _, _) => Single(w) //need to pre-load a separate bitmap (with a separate memCache entry) to be used later in generating the round one
      case Blurred(w, _, _) => Single(w)
      case req => req
    }

    override def loadCached() = data flatMap { a =>
      CancellableFuture.lift(imageLoader.hasCachedBitmap(a, initialReq)) flatMap {
        case true => imageLoader.loadCachedBitmap(a, initialReq).map(Some(_))
        case false => CancellableFuture.successful(None)
      } recover {
        case e: Throwable => None
      }
    }

    override def load() = data flatMap { imageLoader.loadBitmap(_, initialReq, forceDownload) }
  }

  class GifLoader(asset: AssetData, req: BitmapRequest, imageLoader: ImageLoader, assets: AssetStore, forceDownload: Boolean) extends Loader {
    override type Data = Gif

    override def loadCached() = CancellableFuture.lift(imageLoader.hasCachedData(asset)).flatMap {
      case true => imageLoader.loadCachedGif(asset).map(Some(_))
      case false => CancellableFuture.successful(None)
    }.recover {
      case e: Throwable => None
    }

    override def load() = imageLoader.loadGif(asset, forceDownload)

    override def process(gif: Gif, signal: BitmapSignal) = {
      if (gif.frames.length <= 1) {
        val loader = new AssetBitmapLoader(asset, req, imageLoader, assets, forceDownload)
        loader.load() flatMap {loader.process(_, signal)}
      } else {
        var etag = 0 // to make sure signal does not cache dispatched result
        def reserveFrameMemory() = imageLoader.memoryCache.reserve(asset.id, req, gif.width, gif.height * 2)
        def frameLoaded(frame: ABitmap) = signal publish BitmapLoaded(frame, {etag += 1; etag})
        new GifAnimator(gif, reserveFrameMemory, frameLoaded).run()
      }
    }
  }
}


// TODO: we could listen for AssetData changes and restart this signal,
// this isn't really needed currently since ImageAsset will be updated and UI will restart this loading
// but this could be useful in future, if UI forgets to reload or we could stop requiring them to do so
class AssetBitmapSignal(asset: AssetData,
                        req: BitmapRequest,
                        imageLoader: ImageLoader,
                        network: NetworkModeService,
                        assets: BitmapSignal.AssetStore,
                        downloadImagesAlways: Signal[Boolean],
                        forceDownload: Boolean) extends BitmapSignal(req, network, downloadImagesAlways) {
  signal =>

  import BitmapSignal._

  require(asset.isImage, s"Passed non-image data to bitmap signal: $asset")

  override protected def loader(req: BitmapRequest): Loader =
    req match {
      case Single(_, _) | Round(_, _, _) | Blurred(_, _, _) => new AssetBitmapLoader(asset, req, imageLoader, assets, forceDownload)
      case _ =>
        asset.mime match {
          case Mime.Image.Unknown => new MimeCheckLoader(asset, req, imageLoader, assets, forceDownload)
          case Mime.Image.Gif => new GifLoader(asset, req, imageLoader, assets, forceDownload)
          case _ => new AssetBitmapLoader(asset, req, imageLoader, assets, forceDownload)
        }
    }
}

