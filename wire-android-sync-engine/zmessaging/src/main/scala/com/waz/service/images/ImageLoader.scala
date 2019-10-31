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

import java.io._

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.ExifInterface._
import androidx.core.content.FileProvider
import com.waz.bitmap.gif.{Gif, GifReader}
import com.waz.bitmap.{BitmapDecoder, BitmapUtils}
import com.waz.cache.{CacheEntry, CacheService, LocalData}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.model.AssetData.IsImage
import com.waz.model.{Mime, _}
import com.waz.permissions.PermissionsService
import com.waz.service.assets.AssetService
import com.waz.service.downloads.{AssetLoader, AssetLoaderService}
import com.waz.service.images.ImageLoader.Metadata
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.MemoryImageCache
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.IoUtils._
import com.waz.utils.wrappers._
import com.waz.utils.{IoUtils, Serialized, returning}

import scala.collection.immutable.ListSet
import scala.concurrent.Future

trait ImageLoader {
  def memoryCache: MemoryImageCache
  def hasCachedBitmap(asset: AssetData, req: BitmapRequest): Future[Boolean]
  def hasCachedData(asset: AssetData): Future[Boolean]
  def loadCachedBitmap(asset: AssetData, req: BitmapRequest): CancellableFuture[Bitmap]
  def loadBitmap(asset: AssetData, req: BitmapRequest, forceDownload: Boolean): CancellableFuture[Bitmap]
  def loadCachedGif(asset: AssetData): CancellableFuture[Gif]
  def loadGif(asset: AssetData, forceDownload: Boolean): CancellableFuture[Gif]
  def loadRawCachedData(asset: AssetData): CancellableFuture[Option[LocalData]]
  def loadRawImageData(asset: AssetData, forceDownload: Boolean = false): CancellableFuture[Option[LocalData]]
  def saveImageToGallery(asset: AssetData): Future[Option[URI]]
  def getImageMetadata(data: LocalData, mirror: Boolean = false): CancellableFuture[ImageLoader.Metadata]
}

class ImageLoaderImpl(context:                  Context,
                      fileCache:                CacheService,
                      override val memoryCache: MemoryImageCache,
                      bitmapLoader:             BitmapDecoder,
                      permissions:              PermissionsService,
                      loadService:              AssetLoaderService,
                      loader:                   AssetLoader) extends ImageLoader {

  import Threading.Implicits.Background

  protected def tag = "User"
  private implicit val logTag: LogTag = LogTag(s"${LogTag[ImageLoader].value}[$tag]")

  override def hasCachedBitmap(asset: AssetData, req: BitmapRequest): Future[Boolean] = {
    val res = asset match {
      case a@IsImage() => Future.successful(memoryCache.get(asset.id, req, a.width).isDefined)
      case _ => Future.successful(false)
    }
//    verbose(s"Cached bitmap for ${asset.id} with req: $req?: $res")
    res
  }

  override def hasCachedData(asset: AssetData): Future[Boolean] = asset match {
    case IsImage() => (asset.data, asset.source) match {
      case (Some(data), _) if data.nonEmpty => Future.successful(true)
      case (_, Some(uri)) if isLocalUri(uri) => Future.successful(true)
      case _ => fileCache.getEntry(asset.cacheKey).map(_.isDefined)
    }
    case _ => Future.successful(false)
  }

  override def loadCachedBitmap(asset: AssetData, req: BitmapRequest): CancellableFuture[Bitmap] = ifIsImage(asset) { dims =>
//    verbose(s"load cached bitmap for: ${asset.id} and req: $req")
    withMemoryCache(asset.id, req, dims.width) {
      loadLocalAndDecode(asset, decodeBitmap(asset.id, req, _)) map {
        case Some(bmp) => bmp
        case None => throw new Exception(s"No local data for: $asset")
      }
    }
  }

  override def loadBitmap(asset: AssetData, req: BitmapRequest, forceDownload: Boolean): CancellableFuture[Bitmap] = ifIsImage(asset) { dims =>
//    verbose(s"loadBitmap for ${asset.id} and req: $req")
    Serialized(("loadBitmap", asset.id)) {
      // serialized to avoid cache conflicts, we don't want two same requests running at the same time
      withMemoryCache(asset.id, req, dims.width) {
        downloadAndDecode(asset, decodeBitmap(asset.id, req, _), forceDownload)
      }
    }
  }

  override def loadCachedGif(asset: AssetData): CancellableFuture[Gif] = ifIsImage(asset) { _ =>
    loadLocalAndDecode(asset, decodeGif) map {
      case Some(gif) => gif
      case None => throw new Exception(s"No local data for: $asset")
    }
  }

  override def loadGif(asset: AssetData, forceDownload: Boolean): CancellableFuture[Gif] = ifIsImage(asset) { _ =>
    Serialized(("loadBitmap", asset.id, tag)) {
      downloadAndDecode(asset, decodeGif, forceDownload)
    }
  }

  override def loadRawCachedData(asset: AssetData): CancellableFuture[Option[LocalData]] = ifIsImage(asset)(_ => loadLocalData(asset))

  override def loadRawImageData(asset: AssetData, forceDownload: Boolean = false): CancellableFuture[Option[LocalData]] = ifIsImage(asset) { _ =>
//    verbose(s"loadRawImageData: assetId: $asset")
    loadLocalData(asset) flatMap {
      case None => downloadImageData(asset, forceDownload)
      case Some(data) => CancellableFuture.successful(Some(data))
    }
  }

  override def saveImageToGallery(asset: AssetData): Future[Option[URI]] = ifIsImage(asset) { _ =>
    loadRawImageData(asset, forceDownload = true).future flatMap {
      case Some(data) =>
        saveImageToGallery(data, asset.mime)
      case None =>
//        error(s"No image data found for: $asset")
        Future.successful(None)
    }
  }

  override def getImageMetadata(data: LocalData, mirror: Boolean = false): CancellableFuture[ImageLoader.Metadata] =
    Threading.IO {
      val o = BitmapUtils.getOrientation(data.inputStream)
      Metadata(data).withOrientation(if (mirror) Metadata.mirrored(o) else o)
    }

  private def saveImageToGallery(data: LocalData, mime: Mime): Future[Option[URI]] =
    {
      permissions.requestAllPermissions(ListSet(WRITE_EXTERNAL_STORAGE)).flatMap {
        case true =>
          Future {
            val newFile = AssetService.saveImageFile(mime)
            IoUtils.copy(data.inputStream, new FileOutputStream(newFile))
            val scanUri = URI.fromFile(newFile)
            context.sendBroadcast(Intent.scanFileIntent(scanUri))
            val uri = new AndroidURI(FileProvider.getUriForFile(context, context.getApplicationContext.getPackageName + ".fileprovider", newFile))
            Some(uri)
          }(Threading.IO)
        case _ =>
//          warn("permission to save image to gallery denied")
          Future successful None
      }
    }

  private def downloadAndDecode[A](asset: AssetData, decode: LocalData => CancellableFuture[A], forceDownload: Boolean): CancellableFuture[A] =
    loadLocalData(asset).flatMap( localData => downloadAndDecode(asset, decode, localData, 0, forceDownload) )

  private def downloadAndDecode[A](asset: AssetData,
                                   decode: LocalData => CancellableFuture[A],
                                   localData: Option[LocalData],
                                   retry: Int,
                                   forceDownload: Boolean
                                  ): CancellableFuture[A] = {
    localData match {
      case None if retry == 0 =>
        downloadImageData(asset, forceDownload).flatMap(data => downloadAndDecode(asset, decode, data, retry + 1, forceDownload))
      case None if retry >= 1 =>
        CancellableFuture.failed(new Exception(s"No data downloaded for: $asset"))
      case Some(data) => decode(data).recoverWith { case e: Throwable =>
        data match {
          case _ : CacheEntry => downloadAndDecode(asset, decode, None, retry, forceDownload)
          case _ => CancellableFuture.failed(e)
        }
      }
    }
  }

  private def ifIsImage[A](asset: AssetData)(f: Dim2 => A) = asset match {
    case a@IsImage() => f(a.dimensions)
    case _ => throw new IllegalArgumentException(s"Asset is not an image: $asset")
  }

  private def isLocalUri(uri: URI) = uri.getScheme match {
    case ContentResolver.SCHEME_FILE | ContentResolver.SCHEME_ANDROID_RESOURCE => true
    case _ => false
  }

  private def loadLocalAndDecode[A](asset: AssetData, decode: LocalData => CancellableFuture[A]): CancellableFuture[Option[A]] =
    loadLocalData(asset) flatMap {
      case Some(data) => decode(data).map(Some(_)).recover {
        case e: Throwable =>
//          warn(s"loadLocalAndDecode(), decode failed, will delete local data", e)
          data.delete()
          None
      }
      case None =>
        CancellableFuture successful None
    }

  private def loadLocalData(asset: AssetData): CancellableFuture[Option[LocalData]] =
    // wrapped in future to ensure that img.data is accessed from background thread, this is needed for local image assets (especially the one generated from bitmap), see: Images
    CancellableFuture {(asset.data, asset.source)} flatMap {
      case (Some(data), _) if data.nonEmpty =>
//        verbose(s"asset: ${asset.id} contained data already")
        CancellableFuture.successful(Some(LocalData(data)))
      case (_, Some(uri)) if isLocalUri(uri) =>
//        verbose(s"asset: ${asset.id} contained no data, but had a url")
        CancellableFuture.successful(Some(LocalData(AssetLoader.openStream(context, uri), -1)))
      case _ =>
//        verbose(s"asset: ${asset.id} contained no data or a url, trying cached storage")
        CancellableFuture lift fileCache.getEntry(asset.cacheKey)
    }

  private def downloadImageData(asset: AssetData, forceDownload: Boolean): CancellableFuture[Option[LocalData]] = {
//    verbose(s"downloadImageData($asset)")
    loadService.load(asset, force = forceDownload)(loader)
  }

  private def decodeGif(data: LocalData) = Threading.ImageDispatcher {
    data.byteArray.fold(GifReader(data.inputStream))(GifReader(_)).get
  }

  private def decodeBitmap(assetId: AssetId, req: BitmapRequest, data: LocalData): CancellableFuture[Bitmap] = {

    def computeInSampleSize(srcWidth: Int, srcHeight: Int): Int = {
      val pixelCount = srcWidth * srcHeight
      val minSize = if (pixelCount <= ImageAssetGenerator.MaxImagePixelCount) req.width else (req.width * math.sqrt(ImageAssetGenerator.MaxImagePixelCount / pixelCount)).toInt
      BitmapUtils.computeInSampleSize(minSize, srcWidth)
    }

//    verbose(s"decoding bitmap $data")

    for {
      meta <- getImageMetadata(data, req.mirror)
      inSample = computeInSampleSize(meta.width, meta.height)
//      _ = verbose(s"image meta: $meta, inSampleSize: $inSample")
      _ = memoryCache.reserve(assetId, req, meta.width / inSample, meta.height / inSample)
      bmp <- bitmapLoader(() => data.inputStream, inSample, meta.orientation)
      _ = if (bmp.isEmpty) throw new Exception(s"Bitmap decoding failed, got empty bitmap for asset: $assetId")
    } yield bmp
  }

  private def withMemoryCache(assetId: AssetId, req: BitmapRequest, imgWidth: Int)(loader: => CancellableFuture[Bitmap]): CancellableFuture[Bitmap] =
    memoryCache.get(assetId, req, imgWidth) match {
      case Some(image) =>
//        verbose(s"getBitmap($assetId, $req, $imgWidth) - got from cache: $image (${image.getWidth}, ${image.getHeight})")
        CancellableFuture.successful(image)
      case _ =>
        loader map (returning(_) {
//          verbose(s"adding asset to memory cache: $assetId, $req")
          memoryCache.add(assetId, req, _)
        })
    }
}

object ImageLoader {

  //TODO if orientation could be useful ever to other clients, we might want to merge with AssetMetaData.Image
  case class Metadata(width: Int, height: Int, mimeType: String, orientation: Int = ExifInterface.ORIENTATION_UNDEFINED) extends SafeToLog {
    def isRotated: Boolean = orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED

    def withOrientation(orientation: Int) = {
      if (Metadata.shouldSwapDimens(this.orientation) != Metadata.shouldSwapDimens(orientation))
        copy(width = height, height = width, orientation = orientation)
      else
        copy(orientation = orientation)
    }
  }

  object Metadata {

    def apply(data: LocalData): Metadata = {
      val opts = new BitmapFactory.Options
      opts.inJustDecodeBounds = true
      opts.inScaled = false
      withResource(data.inputStream) {BitmapFactory.decodeStream(_, null, opts)}
      Metadata(opts.outWidth, opts.outHeight, opts.outMimeType)
    }

    def shouldSwapDimens(o: Int) = o match {
      case ExifInterface.ORIENTATION_ROTATE_90 | ExifInterface.ORIENTATION_ROTATE_270 | ExifInterface.ORIENTATION_TRANSPOSE | ExifInterface.ORIENTATION_TRANSVERSE => true
      case _ => false
    }

    def mirrored(o: Int) = o match {
      case ORIENTATION_UNDEFINED | ORIENTATION_NORMAL => ORIENTATION_FLIP_HORIZONTAL
      case ORIENTATION_FLIP_HORIZONTAL => ORIENTATION_NORMAL
      case ORIENTATION_FLIP_VERTICAL => ORIENTATION_ROTATE_180
      case ORIENTATION_ROTATE_90 => ORIENTATION_TRANSPOSE
      case ORIENTATION_ROTATE_180 => ORIENTATION_FLIP_VERTICAL
      case ORIENTATION_ROTATE_270 => ORIENTATION_TRANSVERSE
      case ORIENTATION_TRANSPOSE => ORIENTATION_ROTATE_90
      case ORIENTATION_TRANSVERSE => ORIENTATION_ROTATE_270
    }
  }

}
