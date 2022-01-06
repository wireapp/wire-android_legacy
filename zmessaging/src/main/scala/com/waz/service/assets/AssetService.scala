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

import java.io.{File, FileOutputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}

import android.graphics.{Bitmap => ABitmap}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AssetData.UploadTaskKey
import com.waz.model._
import com.waz.model.errors._
import com.waz.service.assets.Asset.{General, Video}
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.AssetClient.{AssetContent, Metadata, Retention, UploadResponse2}
import com.waz.sync.client.{AssetClient, ErrorOrResponse}
import com.wire.signals.CancellableFuture
import com.wire.signals.Signal
import com.waz.utils.streams.CountInputStream
import com.waz.utils.wrappers.Bitmap
import com.waz.utils._
import com.waz.znet2.http.HttpClient._
import com.waz.znet2.http.ResponseCode

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait AssetService {
  def assetSignal(id: GeneralAssetId): Signal[GeneralAsset]
  def assetStatusSignal(id: GeneralAssetId): Signal[(AssetStatus, Option[Progress])]
  def downloadProgress(id: DownloadAssetId): Signal[Progress]
  def uploadProgress(id: UploadAssetId): Signal[Progress]

  def cancelUpload(id: UploadAssetId, message: MessageData): Future[Unit]
  def cancelDownload(id: DownloadAssetId): Unit

  def getAsset(id: AssetId): Future[Asset]

  def save(asset: GeneralAsset): Future[Unit]
  def delete(id: GeneralAssetId): Future[Unit]
  def deleteAll(ids: Set[GeneralAssetId]): Future[Unit]

  def uploadAsset(id: UploadAssetId): CancellableFuture[Asset]
  def createAndSavePreview(asset: UploadAsset): Future[UploadAsset]
  def createAndSaveUploadAsset(content: ContentForUpload,
                               targetEncryption: Encryption,
                               public: Boolean,
                               retention: Retention,
                               messageId: Option[MessageId]): Future[UploadAsset]

  def loadContent(asset: Asset, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput]
  def loadContentById(assetId: AssetId, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput]
  def loadPublicContentById(assetId: AssetId, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput]
  def loadUnsplashProfilePicture(): CancellableFuture[AssetInput]
  def loadUploadContentById(uploadAssetId: UploadAssetId, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput]
}

class AssetServiceImpl(currentDomain: Domain,
                       assetsStorage: AssetStorage,
                       uploadAssetStorage: UploadAssetStorage,
                       downloadAssetStorage: DownloadAssetStorage,
                       assetDetailsService: AssetDetailsService,
                       previewService: AssetPreviewService,
                       restrictions: AssetRestrictionsService,
                       uriHelper: UriHelper,
                       contentCache: AssetContentCache,
                       uploadContentCache: UploadAssetContentCache,
                       assetClient: AssetClient,
                       sync: SyncServiceHandle)
                      (implicit ec: ExecutionContext) extends AssetService with DerivedLogTag {

  override def assetSignal(idGeneral: GeneralAssetId): Signal[GeneralAsset] =
    (idGeneral match {
      case id: AssetId => assetsStorage.signal(id)
      case id: UploadAssetId => uploadAssetStorage.signal(id)
      case id: DownloadAssetId => downloadAssetStorage.signal(id)
    }).map(a => a: GeneralAsset)

  override def assetStatusSignal(idGeneral: GeneralAssetId): Signal[(AssetStatus, Option[Progress])] =
    assetSignal(idGeneral) map {
      case _: Asset => AssetStatus.Done -> None
      case asset: UploadAsset =>
        asset.status match {
          case AssetStatus.Done => asset.status -> None
          case UploadAssetStatus.NotStarted => asset.status -> None
          case _ => asset.status -> Some(Progress(asset.uploaded, Some(asset.size)))
        }
      case asset: DownloadAsset =>
        asset.status match {
          case AssetStatus.Done => asset.status -> None
          case DownloadAssetStatus.NotStarted => asset.status -> None
          case _ => asset.status -> Some(Progress(asset.downloaded, Some(asset.size)))
        }
    }

  override def downloadProgress(id: DownloadAssetId): Signal[Progress] =
    assetStatusSignal(id).collect { case (_, Some(progress)) => progress }

  override def uploadProgress(id: UploadAssetId): Signal[Progress] =
    assetStatusSignal(id).collect { case (_, Some(progress)) => progress }

  override def cancelUpload(id: UploadAssetId, message: MessageData): Future[Unit] = {
    import scala.concurrent.duration._
    for {
      _ <- Cancellable.cancel(UploadTaskKey(id))
      _ <- CancellableFuture.delay(3.seconds).future
      _ <- uploadAssetStorage.update(id, asset => {
        if (asset.status == AssetStatus.Done) asset
        else asset.copy(status = UploadAssetStatus.Cancelled)
      })
      _ <- sync.postAssetStatus(message.id, message.convId, message.ephemeral, UploadAssetStatus.Cancelled)
    } yield ()
  }

  override def cancelDownload(id: DownloadAssetId): Unit = () //TODO

  override def getAsset(id: AssetId): Future[Asset] =
    assetsStorage.get(id)

  override def save(asset: GeneralAsset): Future[Unit] = asset match {
    case a: Asset => assetsStorage.save(a)
    case a: UploadAsset => uploadAssetStorage.save(a)
    case a: DownloadAsset => downloadAssetStorage.save(a)
  }


  override def delete(idGeneral: GeneralAssetId): Future[Unit] = idGeneral match {
    case id: AssetId => assetsStorage.deleteByKey(id)
    case id: UploadAssetId => uploadAssetStorage.deleteByKey(id)
    case id: DownloadAssetId => downloadAssetStorage.deleteByKey(id)
  }

  override def deleteAll(ids: Set[GeneralAssetId]): Future[Unit] = {
    val (assets, uploadAssets, downloadAssets) =
      (mutable.HashSet[AssetId](), mutable.HashSet[UploadAssetId](), mutable.HashSet[DownloadAssetId]())
    ids.foreach {
        case id: AssetId         => assets.add(id)
        case id: DownloadAssetId => downloadAssets.add(id)
        case id: UploadAssetId   => uploadAssets.add(id)
    }
    assetsStorage.deleteAllByKey(assets.toSet)
    downloadAssetStorage.deleteAllByKey(downloadAssets.toSet)
    uploadAssetStorage.deleteAllByKey(uploadAssets.toSet)
  }

  private def loadFromBackend(asset: Asset, callback: Option[ProgressCallback]): CancellableFuture[AssetInput] =
    assetClient.loadAssetContent(asset, callback)
      .flatMap {
        case Left(err) if err.code == ResponseCode.NotFound =>
          contentCache
            .remove(asset.id)
            .map(_ => AssetInput(NotFoundRemote(s"Asset '$asset'")))
            .lift
        case Left(err) =>
          CancellableFuture.successful(AssetInput(NetworkError(err)))
        case Right(fileWithSha) if fileWithSha.sha256 != asset.sha =>
          debug(l"Loaded file size ${fileWithSha.file.length()}")
          CancellableFuture.successful(
            AssetInput(new ValidationError(s"SHA256 is not equal. Expected: ${asset.sha} Actual: ${fileWithSha.sha256} AssetId: ${asset.id}"))
          )
        case Right(fileWithSha) =>
          contentCache.put(asset.id, fileWithSha.file, removeOriginal = true)
            .flatMap(_ => contentCache.getStream(asset.id).map(asset.encryption.decrypt(_)))
            .map(AssetInput(_))
            .lift
      }
      .recoverWith { case err =>
        verbose(l"Can not load asset content from backend. ${showString(err.getMessage)}")
        CancellableFuture.successful(AssetInput(err))
      }

  private def loadFromCache(asset: Asset): CancellableFuture[AssetInput] =
    contentCache.getStream(asset.id).map(asset.encryption.decrypt(_))
      .map(AssetInput(_))
      .recoverWith { case err =>
        verbose(l"Can not load asset content from cache. $err")
        Future.failed(err)
      }
      .lift

  private def loadFromFileSystem(localSource: LocalSource): Option[AssetInput] =
    uriHelper.extractMime(localSource.uri).map {
      case mime if Mime.Video.supported.contains(mime) => uriHelper.assetInput(localSource.uri)
      case _ => uriHelper.assetInput(localSource.uri).validate(localSource.sha)
    }.toOption

  override def loadPublicContentById(assetId: AssetId, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput] =
    assetClient.loadPublicAssetContent(assetId, callback).map {
      case Left(err) => AssetInput(err)
      case Right(i)  => AssetInput(i)
    }

  override def loadUnsplashProfilePicture(): CancellableFuture[AssetInput] =
    assetClient.loadUnsplashProfilePicture().map {
      case Left(err) => AssetInput(err)
      case Right(i)  => AssetInput(i)
    }

  override def loadUploadContentById(uploadAssetId: UploadAssetId, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput] =
    uploadAssetStorage.get(uploadAssetId).flatMap { asset =>
      (asset.assetId, asset.localSource) match {
        case (Some(aId), _) => loadContentById(aId, callback)
        case (_, Some(ls))  => Future.successful(uriHelper.assetInput(ls.uri))
        case _              => uploadContentCache.get(uploadAssetId).map(AssetInput(_))
      }
    }.lift

  override def loadContentById(assetId: AssetId, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput] =
    assetsStorage.get(assetId).flatMap(asset => loadContentFromAsset(asset, callback)).lift

  override def loadContent(asset: Asset, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput] =
    assetsStorage.find(asset.id).flatMap {
      case None              => assetsStorage.save(asset).flatMap(_ => loadFromBackend(asset, callback))
      case Some(fromStorage) => loadContentFromAsset(fromStorage, callback)
    }.lift

  private def loadContentFromAsset(asset: Asset, callback: Option[ProgressCallback] = None): CancellableFuture[AssetInput] =
    asset.localSource match {
      case None         => loadFromCache(asset).recoverWith { case _ => loadFromBackend(asset, callback) }
      case Some(source) => loadFromFileSystem(source) match {
        case None =>
          verbose(l"Can not load content from file system for asset $asset.")
          assetsStorage.save(asset.copy(localSource = None)).flatMap(_ => loadFromBackend(asset, callback)).lift
        case Some(AssetFailure(throwable))  =>
          verbose(l"Can not load content from file system for asset $asset. ${showString(throwable.getMessage)}")
          assetsStorage.save(asset.copy(localSource = None)).flatMap(_ => loadFromBackend(asset, callback)).lift
        case Some(other) =>
          CancellableFuture.successful(other)
      }
    }

  override def uploadAsset(assetId: UploadAssetId): CancellableFuture[Asset] = {
    import com.waz.api.impl.ErrorResponse

    def getUploadAssetContent(asset: UploadAsset): Future[InputStream] = asset.localSource match {
      case Some(LocalSource(uri, _)) => Future.fromTry(uriHelper.openInputStream(uri))
      case None => uploadContentCache.getStream(asset.id)
    }

    def actionsOnCancellation(): Unit = {
      info(l"Asset uploading cancelled: $assetId")
      uploadAssetStorage.update(assetId, _.copy(status = UploadAssetStatus.Cancelled))
    }

    def loadUploadAsset: Future[UploadAsset] = uploadAssetStorage.get(assetId).flatMap { asset =>
      asset.details match {
        case details: General =>
          CancellableFuture.successful(asset.copy(details = details))
        case details =>
          CancellableFuture.failed(FailedExpectationsError(s"We expect that metadata already extracted. Got $details"))
      }
    }

    def doUpload(asset: UploadAsset): ErrorOrResponse[UploadResponse2] = {
      val metadata = Metadata(asset.public, asset.retention)
      val content = AssetContent(
        asset.mime,
        asset.md5,
        () => getUploadAssetContent(asset).map(asset.encryption.encrypt(_, asset.encryptionSalt)),
        Some(asset.size)
      )
      val uploadCallback: ProgressCallback = new FilteredProgressCallback(
        ProgressFilter.steps(100, asset.size),
        new ProgressCallback {
          override def updated(progress: Long, total: Option[Long]): Unit = {
            uploadAssetStorage.update(asset.id, _.copy(uploaded = progress))
            ()
          }
        }
      )
      assetClient.uploadAsset(metadata, content, Some(uploadCallback))
    }

    def handleUploadResult(result: Either[ErrorResponse, UploadResponse2], uploadAsset: UploadAsset): Future[Asset] =
      result match {
        case Left(err) =>
          uploadAssetStorage.update(uploadAsset.id, _.copy(status = UploadAssetStatus.Failed)).flatMap(_ => Future.failed(err))
        case Right(response) =>
          val asset = Asset.create(response.key, response.token, uploadAsset, currentDomain)
          for {
            _ <- assetsStorage.save(asset)
            _ <- uploadAssetStorage.update(uploadAsset.id, _.copy(status = AssetStatus.Done, assetId = Some(asset.id)))
          } yield asset
      }

    def encryptAssetContentAndMoveToCache(asset: Asset): Future[Unit] =
      if (asset.localSource.nonEmpty) Future.successful(())
      else for {
        contentStream <- uploadContentCache.getStream(assetId)
        _             <- contentCache.putStream(asset.id, asset.encryption.encrypt(contentStream))
        _             <- uploadContentCache.remove(assetId)
      } yield ()

    val cancellable = for {
      _                      <- CancellableFuture.lift(Future.successful(()))
      uploadAsset            <- loadUploadAsset.lift
      Some((_, uploadAsset)) <- uploadAssetStorage.update(uploadAsset.id, _.copy(uploaded = 0, status = UploadAssetStatus.InProgress)).lift
      uploadResult           <- doUpload(uploadAsset)
      asset                  <- handleUploadResult(uploadResult, uploadAsset).lift
      _                      <- encryptAssetContentAndMoveToCache(asset).lift
    } yield asset

    cancellable.onCancel(actionsOnCancellation())
  }

  override def createAndSavePreview(uploadAsset: UploadAsset): Future[UploadAsset] = {
    def shouldAssetContainPreview: Boolean = uploadAsset.details match {
      case _: Video => true
      case _        => false
    }

    def getPreparedAssetContent: Future[PreparedContent] = uploadAsset.localSource match {
      case Some(LocalSource(uri, _)) => Future.successful(Content.Uri(uri))
      case None                      => uploadContentCache.get(uploadAsset.id).map(Content.File(uploadAsset.mime, _))
    }

    if (shouldAssetContainPreview) {
      for {
        uploadAssetContent <- getPreparedAssetContent
        content            <- previewService.extractPreview(uploadAsset, uploadAssetContent)
        previewName        =  s"preview_for_${ uploadAsset.id.str}"
        contentForUpload   =  ContentForUpload(previewName, content)
        previewUploadAsset <- createUploadAsset(contentForUpload, uploadAsset.encryption, uploadAsset.public, uploadAsset.retention)
        updatedUploadAsset =  uploadAsset.copy(preview = PreviewNotUploaded(previewUploadAsset.id))
        _                  <- uploadAssetStorage.save(previewUploadAsset)
        _                  <- uploadAssetStorage.save(updatedUploadAsset)
      } yield updatedUploadAsset
    } else {
      for {
        updatedUploadAsset <- Future.successful(uploadAsset.copy(preview = PreviewEmpty))
        _                  <- uploadAssetStorage.save(updatedUploadAsset)
      } yield updatedUploadAsset
    }
  }

  override def createAndSaveUploadAsset(content: ContentForUpload,
                                        targetEncryption: Encryption,
                                        public: Boolean,
                                        retention: Retention,
                                        messageId: Option[MessageId] = None): Future[UploadAsset] = {
    val t0 = System.nanoTime()
    for {
      asset <- createUploadAsset(content, targetEncryption, public, retention, messageId)
      _     <- uploadAssetStorage.save(asset)
      t1    =  System.nanoTime()
      _     =  verbose(l"Asset creation time: ${t1 - t0}ns")
    } yield asset
  }

  private def createUploadAsset(contentForUpload: ContentForUpload,
                                targetEncryption: Encryption,
                                public: Boolean,
                                retention: Retention,
                                messageId: Option[MessageId] = None): Future[UploadAsset] = {
    val assetId = UploadAssetId()
    val encryptionSalt = targetEncryption.randomSalt

    def prepareContent(content: Content): Future[PreparedContent] = content match {
      case content: Content.Uri       =>
        Future.successful(content)
      case Content.File(mime, file)   =>
        for {
          _         <- uploadContentCache.put(assetId, file, removeOriginal = true)
          cacheFile <- uploadContentCache.get(assetId)
        } yield Content.File(mime, cacheFile)
      case Content.Bytes(mime, bytes) =>
        for {
          _    <- uploadContentCache.putBytes(assetId, bytes)
          file <- uploadContentCache.get(assetId)
        } yield Content.File(mime, file)
    }

    def extractLocalSourceAndEncryptedHashesAndSize(content: PreparedContent): Try[(Option[LocalSource], Sha256, MD5, Long)] = {
      val sha256DigestWithUri = content match {
        case Content.Uri(uri) => Some(MessageDigest.getInstance("SHA-256") -> uri)
        case _ => None
      }
      val sha256EncryptedDigest = MessageDigest.getInstance("SHA-256")
      val md5EncryptedDigest = MessageDigest.getInstance("MD5")

      for {
        source        <- content.openInputStream(uriHelper)
        stream1       =  sha256DigestWithUri.fold(source) { case (digest, _) => new DigestInputStream(source, digest) }
        stream2       =  targetEncryption.encrypt(stream1, encryptionSalt)
        stream3       =  new DigestInputStream(stream2, sha256EncryptedDigest)
        stream4       =  new DigestInputStream(stream3, md5EncryptedDigest)
        stream5       =  new CountInputStream(stream4)
        _             =  IoUtils.readFully(stream5)
        localSource   =  sha256DigestWithUri.map { case (digest, uri) => LocalSource(uri, Sha256(digest.digest())) }
        encryptedSha  =  Sha256(sha256EncryptedDigest.digest())
        encryptedMd5  =  MD5(md5EncryptedDigest.digest())
        encryptedSize =  stream5.getBytesRead
      } yield (localSource, encryptedSha, encryptedMd5, encryptedSize)
    }

    for {
      initialContent                                              <- prepareContent(contentForUpload.content)
      (initialDetails, initialMime)                               =  assetDetailsService.extract(initialContent)
      (transformedContent, (transformedDetails, transformedMime)) <- initialDetails match {
        case ImageDetails(dimensions) =>
          recode(assetId, dimensions, initialMime, initialContent).map { c => (c, assetDetailsService.extract(c)) }
        case _ =>
          Future.successful((initialContent, (initialDetails, initialMime)))
      }
      _                                                         <- Future.fromTry(restrictions.validate(transformedContent))
      (localSource, encryptedSha, encryptedMd5, encryptedSize)  <- Future.fromTry(extractLocalSourceAndEncryptedHashesAndSize(transformedContent))
    } yield UploadAsset(
      id             = assetId,
      localSource    = localSource,
      name           = contentForUpload.name,
      md5            = encryptedMd5,
      sha            = encryptedSha,
      mime           = transformedMime,
      preview        = PreviewNotReady,
      uploaded       = 0,
      size           = encryptedSize,
      retention      = retention,
      public         = public,
      encryption     = targetEncryption,
      encryptionSalt = encryptionSalt,
      details        = transformedDetails,
      status         = UploadAssetStatus.NotStarted,
      assetId        = None
    )
  }

  private def compress(assetId: UploadAssetId, bitmap: Bitmap, mime: Mime): Future[PreparedContent] = {
    val (compressFormat, targetMime) = mime match {
      case Mime.Image.Png => (ABitmap.CompressFormat.PNG, Mime.Image.Png)
      case _              => (ABitmap.CompressFormat.JPEG, Mime.Image.Jpg)
    }
    for {
      cacheFile <- uploadContentCache.getOrCreateEmpty(assetId)
      _         =  IoUtils.withResource(new FileOutputStream(cacheFile)) { out => bitmap.compress(compressFormat, 75, out) }
    } yield Content.File(targetMime, cacheFile)
  }

  private def recode(assetId: UploadAssetId, dim: Dim2, mime: Mime, content: PreparedContent): Future[PreparedContent] =
    (content.assetInput(uriHelper), AssetInput.shouldScale(mime, dim)) match {
      case (assetFile: AssetFile, scaleFactor) if scaleFactor > 1 =>
        for {
          resized    <- Future.fromTry(assetFile.toBitmap(AssetInput.bitmapOptions(scaleFactor)))
          compressed <- compress(assetId, resized, mime)
        } yield compressed
      case (assetInput, scaleFactor) if scaleFactor > 1 =>
        val tempFile = File.createTempFile("bitmap", "bmp")
        for {
          // TODO: check if we can use .toInputStream instead
          buffer     <- Future.fromTry(assetInput.toByteArray)
          _          =  IoUtils.writeBytesToFile(tempFile, buffer)
          resized    <- Future.fromTry(AssetInput(tempFile).toBitmap(AssetInput.bitmapOptions(scaleFactor)))
          compressed <- compress(assetId, resized, mime)
        } yield compressed
      case (AssetFile(_), _) =>
        Future.successful(content)
      case (assetInput, _) =>
        for {
          // TODO: check if we can use .toInputStream instead
          file   <- uploadContentCache.getOrCreateEmpty(assetId)
          buffer <- Future.fromTry(assetInput.toByteArray)
          _      =  IoUtils.writeBytesToFile(file, buffer)
        } yield Content.File(mime, file)
    }
}

object AssetService {

  sealed trait BitmapResult
  object BitmapResult {
    case object Empty extends BitmapResult
    case class BitmapLoaded(bitmap: Bitmap, etag: Int = 0) extends BitmapResult {
      override def toString: String = s"BitmapLoaded([${bitmap.getWidth}, ${bitmap.getHeight}], $etag)"
    }
    case class LoadingFailed(ex: Throwable) extends BitmapResult
  }

}
