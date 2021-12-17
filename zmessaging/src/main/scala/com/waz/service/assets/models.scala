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

import java.io.{ByteArrayInputStream, FileInputStream, InputStream}
import java.net.URI

import com.waz.model.GenericContent.{Asset => GenericAsset}
import com.waz.model._
import com.waz.sync.client.AssetClient.Retention
import com.waz.utils.Identifiable
import org.threeten.bp.Duration

import scala.util.{Success, Try}

sealed trait Content {

  def assetInput(uriHelper: UriHelper): AssetInput = this match {
    case Content.Bytes(_, bytes) => AssetInput(new ByteArrayInputStream(bytes))
    case Content.Uri(uri)        => uriHelper.assetInput(uri)
    case Content.File(_, file)   => AssetInput(file)
  }

  def openInputStream(uriHelper: UriHelper): Try[InputStream] = this match {
    case Content.Bytes(_, bytes) => Try { new ByteArrayInputStream(bytes) }
    case Content.Uri(uri)        => uriHelper.openInputStream(uri)
    case Content.File(_, file)   => Try { new FileInputStream(file) }
  }

  def getSize(uriHelper: UriHelper): Try[Long] = this match {
    case Content.Bytes(_, bytes) => Success(bytes.length)
    case Content.Uri(uri)        => uriHelper.extractSize(uri)
    case Content.File(_, file)   => Try { file.length() }
  }

  def getMime(uriHelper: UriHelper): Try[Mime] = this match {
    case Content.Bytes(mime, _)  => Success(mime)
    case Content.Uri(uri)        => uriHelper.extractMime(uri)
    case Content.File(mime, _)   => Success(mime)
  }

}

/**
  * Main purpose of this trait is to mark content which can be used in [[AssetDetails]] extraction process
  */
sealed trait PreparedContent extends Content

object Content {
  case class Bytes(mime: Mime, bytes: Array[Byte]) extends Content
  case class Uri(uri: URI)                         extends PreparedContent
  case class File(mime: Mime, file: java.io.File)  extends PreparedContent
}

/**
  * Be aware that content will be destroyed while upload process in case of [[Content.File]].
  * It means that at some point in the future [[Content.File.file]] will not exist.
  *
  * @param name name for the future asset
  * @param content content for the future asset
  */
case class ContentForUpload(name: String, content: Content)

case class LocalSource(uri: URI, sha: Sha256)

sealed trait Preview
case object PreviewNotReady                              extends Preview
case object PreviewEmpty                                 extends Preview
case class PreviewNotUploaded(rawAssetId: UploadAssetId) extends Preview
case class PreviewUploaded(assetId: AssetId)             extends Preview

sealed trait GeneralAsset {
  def id: GeneralAssetId
  def mime: Mime
  def name: String
  def size: Long
  def details: UploadAssetDetails
}

case class UploadAsset(
    id: UploadAssetId,
    localSource: Option[LocalSource],
    name: String,
    sha: Sha256,
    md5: MD5,
    mime: Mime,
    preview: Preview,
    uploaded: Long,
    size: Long,
    retention: Retention,
    public: Boolean,
    encryption: Encryption,
    encryptionSalt: Option[Salt],
    details: UploadAssetDetails,
    status: UploadAssetStatus,
    assetId: Option[AssetId]
) extends GeneralAsset
    with Identifiable[UploadAssetId]

sealed trait AssetStatus
object AssetStatus {
  case object Done extends UploadAssetStatus with DownloadAssetStatus
}

sealed trait UploadAssetStatus extends AssetStatus
object UploadAssetStatus {
  case object NotStarted extends UploadAssetStatus
  case object InProgress extends UploadAssetStatus
  case object Cancelled  extends UploadAssetStatus
  case object Failed     extends UploadAssetStatus
}

sealed trait DownloadAssetStatus extends AssetStatus
object DownloadAssetStatus {
  case object NotStarted extends DownloadAssetStatus
  case object InProgress extends DownloadAssetStatus
  case object Cancelled  extends DownloadAssetStatus
  case object Failed     extends DownloadAssetStatus
}

final case class Asset(override val id: AssetId,
                       token:           Option[AssetToken], //all not public assets should have an AssetToken
                       sha:             Sha256,
                       mime:            Mime,
                       encryption:      Encryption,
                       localSource:     Option[LocalSource],
                       preview:         Option[AssetId],
                       name:            String,
                       size:            Long,
                       details:         AssetDetails) extends GeneralAsset with Identifiable[AssetId]

case class DownloadAsset(
    id: DownloadAssetId,
    mime: Mime,
    name: String,
    preview: Option[AssetId],
    details: AssetDetails,
    downloaded: Long,
    size: Long,
    status: DownloadAssetStatus
) extends GeneralAsset
    with Identifiable[DownloadAssetId]

object DownloadAsset {

  def create(asset: Messages.Asset): DownloadAsset = {
    val original = Option(asset.getOriginal)

    val (mime, size, detailsInput) = original match {
      case Some(o) => (o.getMimeType, o.getSize, Left(o))
      case _       => (asset.getPreview.getMimeType, asset.getPreview.getSize, Right(asset.getPreview))
    }

    DownloadAsset(
      id = DownloadAssetId(),
      mime = Mime(mime),
      name = original.map(_.getName).getOrElse(""),
      preview = Option(asset.getPreview).flatMap(p => Option(p.getRemote)).map(r => AssetId(r.getAssetId)),
      details = Asset.extractDetails(detailsInput),
      downloaded = 0,
      size = size,
      status = getStatus(asset)
    )
  }

  def getStatus(asset: Messages.Asset): DownloadAssetStatus =
    asset.getStatusCase.getNumber match {
      case Messages.Asset.UPLOADED_FIELD_NUMBER => AssetStatus.Done
      case Messages.Asset.NOT_UPLOADED_FIELD_NUMBER =>
        asset.getNotUploaded match {
          case Messages.Asset.NotUploaded.CANCELLED => DownloadAssetStatus.Cancelled
          case Messages.Asset.NotUploaded.FAILED    => DownloadAssetStatus.Failed
          case _                                    => DownloadAssetStatus.InProgress
        }
      case _ => DownloadAssetStatus.InProgress
    }

}

object Asset {
  type UploadGeneral = UploadAssetDetails
  type NotReady      = DetailsNotReady.type
  type General       = AssetDetails
  type Blob          = BlobDetails.type
  type Image         = ImageDetails
  type Audio         = AudioDetails
  type Video         = VideoDetails

  def extractEncryption(remote: Messages.Asset.RemoteData): Encryption = remote.getEncryption match {
    case Messages.EncryptionAlgorithm.AES_GCM => AES_CBC_Encryption(AESKeyBytes(remote.getOtrKey.toByteArray))
    case Messages.EncryptionAlgorithm.AES_CBC => AES_CBC_Encryption(AESKeyBytes(remote.getOtrKey.toByteArray))
    case _                => NoEncryption
  }

  def extractDetails(either: Either[Messages.Asset.Original, Messages.Asset.Preview]): AssetDetails =
    if (either.fold(_.hasImage, _.hasImage)) {
      val image = either.fold(_.getImage, _.getImage)
      ImageDetails(Dim2(image.getWidth, image.getHeight))
    } else
      either match {
        case Left(original) if original.hasAudio =>
          val audio = original.getAudio
          AudioDetails(Duration.ofMillis(audio.getDurationInMillis), Loudness(audio.getNormalizedLoudness.toByteArray.toVector))
        case Left(original) if original.hasVideo =>
          val video = original.getVideo
          VideoDetails(Dim2(video.getWidth, video.getHeight), Duration.ofMillis(video.getDurationInMillis))
        case _ =>
          BlobDetails
      }

  def create(asset: DownloadAsset, remote: Messages.Asset.RemoteData): Asset =
    Asset(
      id = AssetId(remote.getAssetId),
      token = if (remote.getAssetToken.isEmpty) None else Some(AssetToken(remote.getAssetToken)),
      sha = Sha256(remote.getSha256.toByteArray),
      mime = asset.mime,
      encryption = extractEncryption(remote),
      localSource = None,
      preview = asset.preview,
      name = asset.name,
      size = asset.size,
      details = asset.details
    )

  def create(preview: Messages.Asset.Preview): Asset = {
    val remote = preview.getRemote
    Asset(
      id = AssetId(remote.getAssetId),
      token = if (remote.getAssetToken.isEmpty) None else Some(AssetToken(remote.getAssetToken)),
      sha = Sha256(remote.getSha256.toByteArray),
      mime = Mime(preview.getMimeType),
      encryption = extractEncryption(remote),
      localSource = None,
      preview = None,
      name = s"preview_${System.currentTimeMillis()}",
      size = preview.getSize,
      details = Asset.extractDetails(Right(preview))
    )
  }

  def create(assetId: AssetId, token: Option[AssetToken], uploadAsset: UploadAsset): Asset = {
    require(uploadAsset.details.isInstanceOf[AssetDetails])
    Asset(
      id = assetId,
      token = token,
      mime = uploadAsset.mime,
      sha = uploadAsset.sha,
      name = uploadAsset.name,
      size = uploadAsset.size,
      encryption = uploadAsset.encryption,
      localSource = uploadAsset.localSource,
      preview = uploadAsset.preview match {
        case PreviewUploaded(previewId) => Some(previewId)
        case _ => None
      },
      details = uploadAsset.details.asInstanceOf[AssetDetails]
    )
  }

}

sealed trait UploadAssetDetails
case object DetailsNotReady extends UploadAssetDetails

sealed trait AssetDetails                                       extends UploadAssetDetails
case object BlobDetails                                         extends AssetDetails
case class ImageDetails(dimensions: Dim2)                       extends AssetDetails
case class AudioDetails(duration: Duration, loudness: Loudness) extends AssetDetails
case class VideoDetails(dimensions: Dim2, duration: Duration)   extends AssetDetails

sealed trait ImageTag
object ImageTag {
  case object Preview extends ImageTag
  case object Medium  extends ImageTag
  case object Empty   extends ImageTag
}

case class Loudness(levels: Vector[Byte])
