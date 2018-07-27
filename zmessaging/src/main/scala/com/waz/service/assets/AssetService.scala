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
package com.waz.service.assets


import java.io._

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.{ContentResolver, Context}
import android.media.ExifInterface
import android.os.Environment
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api
import com.waz.api.ProgressIndicator.State
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.api.impl._
import com.waz.bitmap.BitmapUtils
import com.waz.cache.{CacheEntry, CacheService, Expiration, LocalData}
import com.waz.content.WireContentProvider.CacheUri
import com.waz.content._
import com.waz.model.AssetData.{ProcessingTaskKey, UploadTaskKey}
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model.AssetStatus.Order._
import com.waz.model.AssetStatus.{DownloadFailed, UploadCancelled, UploadDone, UploadFailed, UploadInProgress}
import com.waz.model.ErrorData.AssetError
import com.waz.model.Mime.{Audio, Image}
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.ErrorsService
import com.waz.service.assets.AssetService.RawAssetInput
import com.waz.service.assets.AssetService.RawAssetInput._
import com.waz.service.assets.GlobalRecordAndPlayService.AssetMediaKey
import com.waz.service.downloads._
import com.waz.service.images.{ImageAssetGenerator, ImageLoader}
import com.waz.sync.SyncServiceHandle
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.ContentURIs.queryContentUriMetaData
import com.waz.utils._
import com.waz.utils.crypto.ZSecureRandom
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.{Bitmap, URI}

import scala.collection.breakOut
import scala.concurrent.Future.successful
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object AssetService {

  sealed trait RawAssetInput

  object RawAssetInput {
    case class UriInput(uri: URI) extends RawAssetInput
    case class ByteInput(bytes: Array[Byte]) extends RawAssetInput {
      override def toString = s"ByteInput(${bytes.length} bytes)"
    }
    case class BitmapInput(bitmap: Bitmap, orientation: Int = ExifInterface.ORIENTATION_NORMAL) extends RawAssetInput
    case class WireAssetInput(id: AssetId) extends RawAssetInput
  }

  lazy val SaveImageDir = {
    val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator
    val dir = new File(path)
    dir.mkdirs()
    dir
  }

  def assetDir(context: Context) = new File(context.getFilesDir, "assets")

  def sanitizeFileName(name: String) = name.replace(' ', '_').replaceAll("[^\\w]", "")

  def saveImageFile(mime: Mime) = new File(SaveImageDir,  s"${System.currentTimeMillis}.${mime.extension}")

  sealed trait BitmapResult
  object BitmapResult {
    case object Empty extends BitmapResult
    case class BitmapLoaded(bitmap: Bitmap, etag: Int = 0) extends BitmapResult {
      override def toString: LogTag = s"BitmapLoaded([${bitmap.getWidth}, ${bitmap.getHeight}], $etag)"
    }
    case class LoadingFailed(ex: Throwable) extends BitmapResult
  }

}

trait AssetService {
  def assetSignal(id: AssetId): Signal[(AssetData, api.AssetStatus)]
  def downloadProgress(id: AssetId): Signal[ProgressIndicator.ProgressData]
  def cancelDownload(id: AssetId): Future[Unit]
  def uploadProgress(id: AssetId): Signal[ProgressIndicator.ProgressData]
  def cancelUpload(id: AssetId, msg: MessageId): Future[Unit]
  def markUploadFailed(id: AssetId, status: AssetStatus.Syncable): Future[Any] // should be: Future[SyncId]

  def addAssetForUpload(a: AssetForUpload): Future[Option[AssetData]]
  def addAsset(input: RawAssetInput, isProfilePic: Boolean = false, overrideId: Option[AssetId] = None): Future[Option[AssetData]]

  def updateAssets(data: Seq[AssetData]): Future[Set[AssetData]]
  def getLocalData(id: AssetId): CancellableFuture[Option[LocalData]]
  def getAssetData(id: AssetId): Future[Option[AssetData]]
  def saveAssetToDownloads(id: AssetId): Future[Option[File]]
  def saveAssetToDownloads(asset: AssetData): Future[Option[File]]
  def updateAsset(id: AssetId, updater: AssetData => AssetData): Future[Option[AssetData]]
  def getContentUri(id: AssetId): CancellableFuture[Option[URI]]
  def mergeOrCreateAsset(newData: AssetData): Future[Option[AssetData]]
  def removeAssets(ids: Iterable[AssetId]): Future[Unit]
  def removeSource(id: AssetId): Future[Unit]
}

class AssetServiceImpl(storage:         AssetsStorage,
                       generator:       ImageAssetGenerator,
                       cache:           CacheService,
                       context:         Context,
                       messages:        MessagesStorage,
                       loaderService:   AssetLoaderService,
                       loader:          AssetLoader,
                       errors:          ErrorsService,
                       permissions:     PermissionsService,
                       metaService:     MetaDataService,
                       sync:            SyncServiceHandle,
                       media:           GlobalRecordAndPlayService,
                       prefs:           GlobalPreferences) extends AssetService {

  import AssetService._
  import com.waz.threading.Threading.Implicits.Background
  import com.waz.utils.events.EventContext.Implicits.global

  loader.onDownloadDone(markDownloadDone)
  loader.onDownloadFailed { case (id, _) =>
    markDownloadFailed(id)
  }

  messages.onMessageFailed { case (m, _) =>
    if (m.isAssetMessage) markUploadFailed(m.assetId, UploadFailed)
  }

  messages.onDeleted { msgs => removeAssets(msgs.map(id => AssetId(id.str))) }

  storage.onDeleted { assets => media.releaseAnyOngoing(assets.map(AssetMediaKey)(breakOut)) }

  errors.onErrorDismissed {
    case AssetError(ms) => Future.traverse(ms) { messages.remove }
  }

  override def assetSignal(id: AssetId) = storage.signal(id).flatMap[(AssetData, api.AssetStatus)] {
    case asset @ AssetData.WithStatus(status) => (asset.status match {
      case UploadDone => //only if the asset is uploaded, check for a cache entry. Upload state takes precedence over download state
        cache.optSignal(asset.cacheKey).map(_.isDefined) flatMap {
          case true =>
            verbose(s"uploaded asset also has cache entry, must be downloaded. For key: ${asset.cacheKey}")
            Signal.const(api.AssetStatus.DOWNLOAD_DONE)
          case false =>
            loaderService.getLoadProgress(id).map(_.state) map {
              case State.RUNNING => api.AssetStatus.DOWNLOAD_IN_PROGRESS
              case State.COMPLETED => api.AssetStatus.DOWNLOAD_IN_PROGRESS // reporting asset in progress since it should be added to cache before we change the state
              case _ => status.status
            }
        }
      case _ => Signal.const(status.status)
    }).map(st => (asset, st))

    case _ => Signal.empty[(AssetData, api.AssetStatus)]
  }

  override def downloadProgress(id: AssetId) = loaderService.getLoadProgress(id)

  override def cancelDownload(id: AssetId) = loaderService.cancel(id)

  override def uploadProgress(id: AssetId) = Signal const ProgressData.Indefinite // TODO

  override def cancelUpload(id: AssetId, msg: MessageId) =
    for {
      _ <- loaderService.cancel(id)
      _ <- AssetProcessing.cancel(ProcessingTaskKey(id))
      _ <- Cancellable.cancel(UploadTaskKey(id))
      _ <- markUploadFailed(id, UploadCancelled)
    } yield ()

  override def markUploadFailed(id: AssetId, status: AssetStatus.Syncable) =
    storage.updateAsset(id, { a => if (a.status > UploadInProgress) a else a.copy(status = status) }) flatMap {
      case Some(_) =>
        messages.get(MessageId(id.str)) flatMap {
          case Some(m) => sync.postAssetStatus(m.id, m.convId, m.ephemeral, status)
          case None =>
            warn(s"No message found for asset upload: $id")
            Future.successful(())
        }
      case _ =>
        Future.successful(())
    }

  //TODO remove use of AssetForUpload and then this method can go
  override def addAssetForUpload(a: AssetForUpload) = a match {
    case ContentUriAssetForUpload(id, uri)   => addAsset(UriInput(uri), overrideId = Some(id))
    case AudioAssetForUpload(id, data, _, _) => addAsset(UriInput(CacheUri(data.data.key, context)), overrideId = Some(id))
  }

  //note, overrideId is only used for AssetForUpload (always a uri-based input). Once we remove the AssetForUplaod, we can
  //also remove this parameter
  override def addAsset(input: RawAssetInput, isProfilePic: Boolean = false, overrideId: Option[AssetId] = None) = {
    verbose(s"addAsset: input: $input, isProfilePic: $isProfilePic")

    def generateImageData(asset: AssetData, isProfilePic: Boolean = false) =
      generator.generateWireAsset(asset, isProfilePic).future.flatMap { data =>
        storage.mergeOrCreateAsset(data).map(_ => data)
      }.map(Some(_))

    def loadAssetData(asset: AssetData) =
      loaderService.load(asset, force = true)(loader) //will ensure that PCM audio files get encoded
        .flatMap {
        case Some(entry) => CancellableFuture.successful(entry)
        case None =>
          errors.addAssetFileNotFoundError(asset.id)
          CancellableFuture.failed(new NoSuchElementException("no data available after download"))
      }

    input match {
      case UriInput(uri) =>
        for {
          info <- queryContentUriMetaData(context, uri)
          asset = AssetData(
            id          = overrideId.getOrElse(AssetId()),
            mime        = info.mime,
            sizeInBytes = info.size.getOrElse(0),
            name        = info.name,
            source      = Some(uri),
            metaData = info.mime match {
              case Image() => AssetMetaData.Image(context, uri, Tag.Medium)
              case _       => Option.empty[AssetMetaData]
            }
          )
          saved <- info.mime match {
            case Image() => generateImageData(asset, isProfilePic)
            case _       =>
              //trigger calculation of preview and meta data for asset.
              //Do this in parallel to ensure that the message is created quickly.
              //pass to asset processing so sending can wait on the result
              AssetProcessing(ProcessingTaskKey(asset.id)) {
                for {
                  entry   <- loadAssetData(asset)
                  updated <- updateMetaData(asset, entry)
                } yield updated
              }
              storage.insert(asset).map(Some(_))
          }
        } yield {
          verbose(s"created asset: $saved")
          saved
        }

      case ByteInput(bytes) =>
        //TODO determine mimetype of byte array, for now it's only used for images
        generateImageData(AssetData.newImageAsset(tag = Medium).copy(sizeInBytes = bytes.length, data = Some(bytes)), isProfilePic)

      case BitmapInput(bm, orientation) =>
        val mime   = Mime(BitmapUtils.getMime(bm))
        val (w, h) = if (ImageLoader.Metadata.shouldSwapDimens(orientation)) (bm.getHeight, bm.getWidth) else (bm.getWidth, bm.getHeight)
        val asset = AssetData.newImageAsset(tag = Medium).copy(sizeInBytes = bm.getByteCount)
        val imageData = loader.loadFromBitmap(asset.id, bm, orientation)

        generateImageData(asset.copy(
          mime = mime,
          metaData = Some(AssetMetaData.Image(Dim2(w, h), Medium)),
          data = {
            verbose(s"data requested, compress completed: ${imageData.isCompleted}")
            // XXX: this is ugly, but will only be accessed from bg thread and very rarely, so we should be fine with that hack
            LoggedTry(Await.result(imageData, 15.seconds)).toOption
          }
        ), isProfilePic)

      case WireAssetInput(id) => getAssetData(id)
    }
  }

  override def updateAssets(data: Seq[AssetData]) =
    storage.updateOrCreateAll(data.map(d => d.id -> { (_: Option[AssetData]) => d })(collection.breakOut))

  override def updateAsset(id: AssetId, updater: AssetData => AssetData) = storage.updateAsset(id, updater)

  override def getLocalData(id: AssetId) =
    CancellableFuture lift storage.get(id) flatMap {
      case None => CancellableFuture successful None
      case Some(asset) => loaderService.load(asset)(loader) map { res =>
        if (res.isEmpty) errors.addAssetFileNotFoundError(id)
        res
      }
    }

  override def getAssetData(id: AssetId) = storage.get(id)

  override def mergeOrCreateAsset(assetData: AssetData) = storage.mergeOrCreateAsset(assetData)

  override def removeAssets(ids: Iterable[AssetId]) = Future.traverse(ids)(removeSource).flatMap(_ => storage.removeAll(ids))

  override def removeSource(id: AssetId) = storage.get(id)
    .collect { case Some(asset) if asset.isVideo || asset.isAudio => asset.source }
    .collect { case Some(source) if shouldDelete(source) => new File(source.getPath) }
    .collect { case file if file.exists() => file.delete() }

  private def shouldDelete(uri: URI) = uri.getScheme match {
    case ContentResolver.SCHEME_FILE | ContentResolver.SCHEME_ANDROID_RESOURCE => false
    case _ => true
  }

  private def updateMetaData(oldAsset: AssetData, entry: LocalData): CancellableFuture[Option[AssetData]] = {
    val (mime, nm) = entry match {
      case e: CacheEntry => (e.data.mimeType, e.data.fileName.orElse(oldAsset.name))
      case _ => (oldAsset.mime, oldAsset.name)
    }
    val asset = oldAsset.copy(mime = mime, name = nm)
    for {
      meta     <- metaService.loadMetaData(asset, entry)
      prev     <- metaService.loadPreview(asset, entry)
      updated  <- CancellableFuture lift storage.updateAsset(asset.id,
        _.copy(
          metaData = meta,
          mime = mime,
          name = nm,
          previewId = prev.map(_.id),
          sizeInBytes = entry.length))
    } yield returning(updated)(a => verbose(s"Generated preview and meta data for ${asset.id}"))
  }

  private def markDownloadFailed(id: AssetId) = storage.updateAsset(id, _.copy(status = DownloadFailed))

  private def markDownloadDone(id: AssetId) = storage.updateAsset(id, _.copy(status = UploadDone))

  override def getContentUri(id: AssetId) =
    CancellableFuture.lift(storage.get(id)).flatMap {
      case Some(a: AssetData) =>
        verbose(s"getContentUri for: $id")
        loaderService.load(a, force = true)(loader) flatMap {
          case Some(entry: CacheEntry) =>
            CancellableFuture successful {
              val uri = Some(CacheUri(entry.data, context))
              verbose(s"Created cache entry uri: $uri for asset: $id")
              uri
            }
          case Some(data) =>
            CancellableFuture lift cache.addStream(a.cacheKey, data.inputStream, a.mime, a.name, Some(cache.intCacheDir))(Expiration.in(12.hours)) map { e =>
              val uri = Some(CacheUri(e.data, context))
              verbose(s"Created cache entry, and then uri: $uri for asset: ${a.id}")
              uri
            }
          case None =>
            CancellableFuture successful None
        }
      case _ =>
        warn(s"asset not found: $id")
        CancellableFuture successful None
    }

  override def saveAssetToDownloads(id: AssetId) = storage.get(id).flatMapOpt(saveAssetToDownloads)

  override def saveAssetToDownloads(asset: AssetData) = {

    def nextFileName(baseName: String, retry: Int) =
      if (retry == 0) baseName else s"${retry}_$baseName"

    def getTargetFile(dir: File): Option[File] = {
      val baseName = asset.name.getOrElse("downloaded_file." + asset.mime.extension).replace("/", "") // XXX: should get default file name form resources
      // prepend a number to the name to get unique file name,
      // will try sequential numbers from 0 - 10 first, and then fallback to random ones
      // will give up after 100 tries
      val prefix = ((0 to 10).iterator ++ Iterator.continually(ZSecureRandom.nextInt(10000))).take(100)
      prefix.map(i => new File(dir, nextFileName(baseName, i))).find(!_.exists())
    }

    def saveAssetData(file: File) =
      loaderService.load(asset, force = true)(loader).future.map {
        case Some(data) =>
          //TODO Dean: remove after v2 transition period
          //Trigger updating of meta data for assets generated (and downloaded) from old AnyAssetData type.
          asset.mime match {
            case Mime.Video() if asset.metaData.isEmpty || asset.previewId.isEmpty => updateMetaData(asset, data)
            case Mime.Audio() if asset.metaData.isEmpty => updateMetaData(asset, data)
            case _ => CancellableFuture.successful(Some(asset))
          }

          IoUtils.copy(data.inputStream, new FileOutputStream(file))
          Some(file)
        case None =>
          None
      } (Threading.IO)

    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (dir.isDirectory) {
      permissions.requestAllPermissions(Set(WRITE_EXTERNAL_STORAGE)).flatMap {
        case true =>
          getTargetFile(dir).fold(successful(Option.empty[File]))(saveAssetData)
        case _ =>
          warn("permission to save asset to downloads denied")
          successful(None)
      }
    } else successful(None)
  }
}
