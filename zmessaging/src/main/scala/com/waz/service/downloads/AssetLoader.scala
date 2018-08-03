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
package com.waz.service.downloads

import java.io._
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

import android.graphics.Bitmap
import android.media.ExifInterface
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{LogTag, debug, verbose, warn}
import com.waz.api.NetworkMode
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.api.impl.ProgressIndicator.Callback
import com.waz.bitmap.video.VideoTranscoder
import com.waz.bitmap.{BitmapDecoder, BitmapUtils}
import com.waz.cache.{CacheEntry, CacheService}
import com.waz.content.AssetsStorage
import com.waz.content.UserPreferences.DownloadImagesAlways
import com.waz.content.WireContentProvider.CacheUri
import com.waz.model.AssetData.{RemoteData, WithExternalUri, WithProxy, WithRemoteData}
import com.waz.model._
import com.waz.service.assets.AudioTranscoder
import com.waz.service.tracking.TrackingService
import com.waz.service.{NetworkModeService, UserService, ZMessaging}
import com.waz.sync.client.AssetClient
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.MemoryImageCache
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.wrappers.{Context, URI}
import com.waz.utils.{CancellableStream, returning}
import com.waz.znet2.http
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{Method, Request, RequestInterceptor, ResponseCode}

import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

trait AssetLoader {

  def onDownloadStarting: EventStream[AssetId]
  def onDownloadDone:     EventStream[AssetId]
  def onDownloadFailed:   EventStream[(AssetId, ErrorResponse)]

  //guarantees to either return a defined cache entry, or throw an exception
  def loadAsset(asset: AssetData, callback: Callback, force: Boolean): CancellableFuture[CacheEntry]
  def loadFromBitmap(assetId: AssetId, bitmap: Bitmap, orientation: Int = ExifInterface.ORIENTATION_NORMAL): Future[Array[Byte]]
}

class AssetLoaderImpl(context:         Context,
                      assetStorage:    Option[AssetsStorage],
                      network:         NetworkModeService,
                      client:          AssetClient,
                      audioTranscoder: AudioTranscoder,
                      videoTranscoder: VideoTranscoder,
                      cache:           CacheService,
                      imgCache:        MemoryImageCache,
                      bitmapDecoder:   BitmapDecoder,
                      tracking:        TrackingService)
                     (implicit
                      urlCreator: UrlCreator,
                      authInterceptor: RequestInterceptor) extends AssetLoader {

  private lazy val downloadAlways = Option(ZMessaging.currentAccounts).map(_.activeZms).map {
    _.flatMap {
      case None => Signal.const(false)
      case Some(z) => z.userPrefs.preference(DownloadImagesAlways).signal
    }
  }.getOrElse {
    warn("No CurrentAccounts available - this may be being called too early...")
    Signal.const(true)
  }

  private lazy val downloadEnabled = (for {
  //Will be set by UserPreferences when available, defaults to always download (false) otherwise.
    downloadAlways <- downloadAlways
    onWifi         <- network.networkMode.map(_ == NetworkMode.WIFI)
  } yield downloadAlways || onWifi).disableAutowiring()

  import AssetLoader._
  import com.waz.threading.Threading.Implicits.Background

  override val onDownloadStarting = EventStream[AssetId]()
  override val onDownloadDone     = EventStream[AssetId]()
  override val onDownloadFailed   = EventStream[(AssetId, ErrorResponse)]()

  override def loadAsset(asset: AssetData, callback: Callback, force: Boolean): CancellableFuture[CacheEntry] = {
    verbose(s"loadAsset: ${asset.id}, isDownloadable?: ${asset.isDownloadable}, force?: $force, mime: ${asset.mime}")
    returning(asset match {
      case _ if asset.mime == Mime.Audio.PCM => transcodeAudio(asset, callback)
      case _ => CancellableFuture.lift(cache.getEntry(asset.cacheKey)).flatMap {
        case Some(cd) => CancellableFuture.successful(cd)
        case None if asset.isDownloadable && force => download(asset, callback)
        case None if asset.isDownloadable => CancellableFuture.lift(downloadEnabled.head).flatMap {
          case true => download(asset, callback)
          case false => CancellableFuture.failed(DownloadOnWifiOnlyException)
        }
        case _ =>
          (asset.mime, asset.source) match {
            case (Mime.Video(), Some(uri)) => transcodeVideo(asset.cacheKey, asset.mime, asset.name, uri, callback)
            case (_, Some(uri))            => loadFromUri(asset.cacheKey, asset.mime, asset.name, uri)
            case _                         => CancellableFuture.failed(new Exception(s"Not enough information to load asset data: ${asset.id}"))
          }
      }
    })(_.failed.foreach(throw _))
  }

  private def download(asset: AssetData, callback: Callback) = {
    onDownloadStarting ! asset.id

    def finish(a: AssetData, entry: CacheEntry) = {
      CancellableFuture.lift(cache.move(a.cacheKey, entry, a.mime, a.name).andThen { case _ =>
        onDownloadDone ! asset.id
      })
    }

    //TODO Strange situation, only in one case we need request without authorization. Maybe we can get rid of this special case
    (asset match {
      case WithRemoteData(RemoteData(Some(rId), token, otrKey, sha, _)) =>
        verbose(s"Downloading wire asset: ${asset.id}: $rId")
        val path = AssetClient.getAssetPath(rId, otrKey, asset.convId)
        val headers = token.fold(Map.empty[String, String])(t => Map("Asset-Token" -> t.str))
        val request = Request.Get(relativePath = path, headers = http.Headers(headers))
        client.loadAsset(request, otrKey, sha, callback)

      case WithExternalUri(uri) =>
        verbose(s"Downloading external asset: ${asset.id}: $uri")
        val request = Request.create(method = Method.Get, url = new URL(uri.toString))
        val resp = client.loadAsset(request, callback = callback)
        if (uri == UserService.UnsplashUrl)
          resp.flatMap {
            case Right(entry) => CancellableFuture.successful(Right(entry))
            case Left(_) =>
              CancellableFuture.lift(cache.addStream(CacheKey(), context.getAssets.open("unsplash_default.jpeg"), Mime.Image.Jpg)
                .map(Right(_))
                .recover {
                  case NonFatal(e) => Left(internalError(s"Failed to load default unsplash image, ${e.getMessage}"))
                })
          }
        else resp

      case WithProxy(proxy) =>
        verbose(s"Downloading asset from proxy: ${asset.id}: $proxy")
        val request = Request.Get(relativePath = proxy)
        client.loadAsset(request, callback = callback)


      case _ => CancellableFuture.successful(Left(internalError(s"Tried to download asset ${asset.id} without enough information to complete download")))
    }).flatMap {
      case Right(entry) => finish(asset, entry)
      case Left(err) =>
        if (err.code == ResponseCode.NotFound) {
          verbose(s"Asset not found. Removing from local storage $asset")
          assetStorage.foreach(storage => storage.remove(asset.id))
        }
        if (err.isFatal) onDownloadFailed ! (asset.id, err)
        CancellableFuture.failed(DownloadFailedException(err))
    }
  }

  private def transcodeAudio(asset: AssetData, callback: Callback) = {
    verbose(s"transcodeAudio: asset: ${asset.id}, cachekey: ${asset.cacheKey}, mime: ${asset.mime}, uri: ${asset.source}")
    val entry = cache.createManagedFile()
    val uri = asset.source.getOrElse(CacheUri(asset.cacheKey, context))

    audioTranscoder(uri, entry.cacheFile, callback).flatMap { _ =>
      verbose(s"loaded audio from ${asset.cacheKey}, resulting file size: ${entry.length}")
      CancellableFuture.lift(cache.move(asset.cacheKey, entry, Mime.Audio.MP4, asset.name, cacheLocation = Some(cache.cacheDir)))
    }.recoverWith {
      case ex: CancelException => CancellableFuture.failed(ex)
      case NonFatal(ex) =>
        tracking.exception(ex, s"audio transcoding failed for uri")
        CancellableFuture.failed(ex)
    }
  }

  private def openStream(uri: URI) = AssetLoader.openStream(context, uri)

  private def transcodeVideo(cacheKey: CacheKey, mime: Mime, name: Option[String], uri: URI, callback: Callback) = {
    verbose(s"transcodeVideo: cacheKey: $cacheKey, mime: $mime, name: $name, uri: $uri")
    val entry = cache.createManagedFile()
    // TODO: check input type, size, bitrate, maybe we don't need to transcode it
    returning(videoTranscoder(uri, entry.cacheFile, callback).flatMap { _ =>
      verbose(s"loaded video from $cacheKey, resulting file size: ${entry.length}")
      CancellableFuture.lift(cache.move(cacheKey, entry, Mime.Video.MP4, if (mime == Mime.Video.MP4) name else name.map(_ + ".mp4"), cacheLocation = Some(cache.cacheDir)))
        .map { entry =>
          //TODO AN-5742 Use CacheService to store temp vids instead of handling them manually
          entry.file.foreach { file => if(file.getName.startsWith("VID_")) file.delete() }
          entry
        }
    }.recoverWith {
      case ex: CancelException => CancellableFuture.failed(ex)
      case NonFatal(ex) =>
        tracking.exception(ex, s"video transcoding failed for uri")
        addStreamToCache(cacheKey, mime, name, openStream(uri))
    })(_.failed.foreach(throw _))
  }

  private def addStreamToCache(cacheKey: CacheKey, mime: Mime, name: Option[String], stream: => InputStream) = {
    val promise = Promise[CacheEntry]
    val cancelled = new AtomicBoolean(false)

    // important: this file needs to be stored unencrypted so we can process it (for e.g. video thumbnails); specifying an explicit cache location "forces" that (for now)
    promise.tryCompleteWith {
      cache.addStream(cacheKey, new CancellableStream(stream, cancelled), mime, name, cacheLocation = Some(cache.cacheDir), execution = Threading.BlockingIO)
    }
    new CancellableFuture(promise) {
      override def cancel()(implicit tag: LogTag): Boolean = cancelled.compareAndSet(false, true)
    }
  }

  private def loadFromUri(cacheKey: CacheKey, mime: Mime, name: Option[String], uri: URI) = {
    verbose(s"loadFromUri: cacheKey: $cacheKey, mime: $mime, name: $name, uri: $uri")
    addStreamToCache(cacheKey, mime, name, openStream(uri))
  }

  override def loadFromBitmap(assetId: AssetId, bitmap: Bitmap, orientation: Int = ExifInterface.ORIENTATION_NORMAL): Future[Array[Byte]] = Future {
    val req = BitmapRequest.Regular(bitmap.getWidth)
    val mime = Mime(BitmapUtils.getMime(bitmap))

    imgCache.reserve(assetId, req, bitmap.getWidth, bitmap.getHeight)
    val img: Bitmap = bitmapDecoder.withFixedOrientation(bitmap, orientation)
    imgCache.add(assetId, req, img)
    verbose(s"compressing $assetId")
    val before = System.nanoTime
    val bos = new ByteArrayOutputStream(65536)
    val format = BitmapUtils.getCompressFormat(mime.str)
    img.compress(format, 85, bos)
    val bytes = bos.toByteArray
    val duration = (System.nanoTime - before) / 1e6d
    debug(s"compression took: $duration ms (${img.getWidth} x ${img.getHeight}, ${img.getByteCount} bytes -> ${bytes.length} bytes, ${img.getConfig}, $mime, $format)")
    bytes
  }(Threading.ImageDispatcher)

}

object AssetLoader {

  abstract class DownloadException extends Exception {
    def isRecoverable: Boolean
  }

  case class DownloadFailedException(error: ErrorResponse) extends DownloadException {
    override def isRecoverable: Boolean = !error.isFatal
    override def getMessage = s"Download failed with error: $error, should retry?: $isRecoverable"
  }

  case object DownloadOnWifiOnlyException extends DownloadException {
    override def isRecoverable = false
    override def getMessage = "Attempted to download image when not on Wifi and DownloadImagesAlways is set to false"
  }

  def openStream(context: Context, uri: URI) = {
    val cr = context.getContentResolver
    Option(cr.openInputStream(URI.unwrap(uri)))
      .orElse(Option(cr.openFileDescriptor(URI.unwrap(uri), "r")).map(file => new FileInputStream(file.getFileDescriptor)))
      .getOrElse(throw new FileNotFoundException(s"Can not load image from: $uri"))
  }
}
