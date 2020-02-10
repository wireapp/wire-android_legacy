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

import android.util.Base64
import com.waz.model._
import com.waz.model.errors.FailedExpectationsError
import com.waz.service.assets
import com.waz.sync.client.AssetClient.Retention

trait Serializer[From, To] {
  def serialize(value: From): To
}

trait Deserializer[From, To] {
  def deserialize(value: To): From
}

trait Codec[From, To] extends Serializer[From, To] with Deserializer[From, To] {
  def imap[B](from: B => From, to: From => B): Codec[B, To] =
    Codec.create(from andThen serialize, deserialize _ andThen to)
}

object Codec {
  def apply[From, To](implicit codec: Codec[From, To]): Codec[From, To] = codec
  def create[From, To](to: From => To, from: To => From): Codec[From, To] =
    new Codec[From, To] {
      override def serialize(value: From): To   = to(value)
      override def deserialize(value: To): From = from(value)
    }
}

trait StorageCodecs {
  import java.net.URI

  import com.waz.model.{AssetId, AssetToken, Sha256}
  import io.circe.parser._
  import io.circe.syntax._
  import io.circe.{Decoder, Encoder}

  def asBase64String(bytes: Array[Byte]): String = Base64.encodeToString(bytes, Base64.NO_PADDING)
  def asBytes(base64String: String): Array[Byte] = Base64.decode(base64String, Base64.NO_PADDING)

  implicit val SaltCodec: Codec[Salt, String] = new Codec[Salt, String] {
    override def serialize(value: Salt): String = asBase64String(value.bytes)
    override def deserialize(value: String): Salt = Salt(asBytes(value))
  }

  implicit val EncryptionCodec: Codec[Encryption, String] = new Codec[Encryption, String] {
    val Unencrypted    = ""
    val AES_CBC_Prefix = "AES_CBS__"

    override def serialize(value: Encryption): String = value match {
      case NoEncryption            => Unencrypted
      case AES_CBC_Encryption(key) => AES_CBC_Prefix + asBase64String(key.bytes)
    }

    override def deserialize(value: String): Encryption = value match {
      case Unencrypted => NoEncryption
      case str if str.startsWith(AES_CBC_Prefix) =>
        AES_CBC_Encryption(AESKey2(asBytes(str.substring(AES_CBC_Prefix.length))))
    }
  }

  implicit val RawPreviewCodec: Codec[Preview, String] = new Codec[Preview, String] {
    val NotReadyStr = "not_ready"
    val WithoutPreviewStr = "without"
    val NotUploadedPrefix = "not_uploaded__"
    val UploadedPrefix = "uploaded__"

    override def serialize(value: Preview): String = value match {
      case PreviewNotReady => NotReadyStr
      case PreviewEmpty => WithoutPreviewStr
      case PreviewNotUploaded(rawAssetId) => NotUploadedPrefix + rawAssetId.str
      case PreviewUploaded(assetId) => UploadedPrefix + assetId.str
    }

    override def deserialize(value: String): Preview = value match {
      case NotReadyStr => PreviewNotReady
      case WithoutPreviewStr => PreviewEmpty
      case str if str.startsWith(NotUploadedPrefix) =>
        PreviewNotUploaded(UploadAssetId(str.substring(NotUploadedPrefix.length)))
      case str if str.startsWith(UploadedPrefix) =>
        PreviewUploaded(AssetId(str.substring(UploadedPrefix.length)))
    }
  }

  implicit val RetentionCodec: Codec[Retention, Int] = new Codec[Retention, Int] {
    val Volatile                = 1
    val Eternal                 = 2
    val EternalInfrequentAccess = 3
    val Expiring                = 4
    val Persistent              = 5

    override def serialize(value: Retention): Int = value match {
      case Retention.Volatile                => Volatile
      case Retention.Eternal                 => Eternal
      case Retention.EternalInfrequentAccess => EternalInfrequentAccess
      case Retention.Expiring                => Expiring
      case Retention.Persistent              => Persistent
    }

    override def deserialize(value: Int): Retention = value match {
      case Volatile                => Retention.Volatile
      case Eternal                 => Retention.Eternal
      case EternalInfrequentAccess => Retention.EternalInfrequentAccess
      case Expiring                => Retention.Expiring
      case Persistent              => Retention.Persistent
    }
  }

  implicit val AssetUploadStatusCodec: Codec[UploadAssetStatus, Int] = new Codec[UploadAssetStatus, Int] {
    val NotStarted = 1
    val InProgress = 2
    val Done       = 3
    val Cancelled  = 4
    val Failed     = 5

    override def serialize(value: UploadAssetStatus): Int = value match {
      case UploadAssetStatus.NotStarted => NotStarted
      case UploadAssetStatus.InProgress => InProgress
      case assets.AssetStatus.Done             => Done
      case UploadAssetStatus.Cancelled  => Cancelled
      case UploadAssetStatus.Failed     => Failed
    }

    override def deserialize(value: Int): UploadAssetStatus = value match {
      case NotStarted => UploadAssetStatus.NotStarted
      case InProgress => UploadAssetStatus.InProgress
      case Done       => assets.AssetStatus.Done
      case Cancelled  => UploadAssetStatus.Cancelled
      case Failed     => UploadAssetStatus.Failed
    }
  }

  implicit val AssetDownloadStatusCodec: Codec[DownloadAssetStatus, Int] = new Codec[DownloadAssetStatus, Int] {
    val NotStarted = 1
    val InProgress = 2
    val Done       = 3
    val Cancelled  = 4
    val Failed     = 5

    override def serialize(value: DownloadAssetStatus): Int = value match {
      case DownloadAssetStatus.NotStarted => NotStarted
      case DownloadAssetStatus.InProgress => InProgress
      case assets.AssetStatus.Done               => Done
      case DownloadAssetStatus.Cancelled  => Cancelled
      case DownloadAssetStatus.Failed     => Failed
    }

    override def deserialize(value: Int): DownloadAssetStatus = value match {
      case NotStarted => DownloadAssetStatus.NotStarted
      case InProgress => DownloadAssetStatus.InProgress
      case Done       => assets.AssetStatus.Done
      case Cancelled  => DownloadAssetStatus.Cancelled
      case Failed     => DownloadAssetStatus.Failed
    }
  }

  implicit val AssetIdCodec: Codec[AssetId, String] = Codec.create(_.str, AssetId.apply)

  implicit val MessageIdCodec: Codec[MessageId, String] = Codec.create(_.str, MessageId.apply)

  implicit val RawAssetIdCodec: Codec[UploadAssetId, String] = Codec.create(_.str, UploadAssetId.apply)

  implicit val Sha256Codec: Codec[Sha256, Array[Byte]] = Codec.create(_.bytes, Sha256.apply)

  implicit val Md5Codec: Codec[MD5, Array[Byte]] = Codec.create(_.bytes, MD5.apply)

  implicit val AssetTokenCodec: Codec[AssetToken, String] = Codec.create(_.str, AssetToken.apply)

  implicit val URICodec: Codec[URI, String] = Codec.create(_.toString, new URI(_))

  implicit val MimeCodec: Codec[Mime, String] = Codec.create(_.str, Mime.apply)

  implicit val GeneralAssetIdCodec: Codec[GeneralAssetId, String] = new Codec[GeneralAssetId, String] {
    private val RawAssetPrefix = "raw_"
    private val InProgressAssetPrefix = "in_progress_"

    override def deserialize(value: String): GeneralAssetId = {
      if (value.startsWith(RawAssetPrefix)) UploadAssetId(value.substring(RawAssetPrefix.length))
      else if (value.startsWith(InProgressAssetPrefix)) DownloadAssetId(value.substring(InProgressAssetPrefix.length))
      else AssetId(value)
    }

    override def serialize(value: GeneralAssetId): String = value match {
      case AssetId(str) => str
      case UploadAssetId(str) => RawAssetPrefix + str
      case DownloadAssetId(str) => InProgressAssetPrefix + str
    }
  }

  implicit val UserPictureCodec: Codec[Picture, String] = Codec[GeneralAssetId, String].imap({
    case PictureNotUploaded(id) => id
    case PictureUploaded(id) => id
  }, {
    case id: AssetId => PictureUploaded(id)
    case id: UploadAssetId => PictureNotUploaded(id)
    case id => throw FailedExpectationsError(s"We do not expect asset id of type ${id.getClass.getSimpleName}. Asset id: $id.")
  })

  implicit def JsonCodec[T: Encoder: Decoder]: Codec[T, String] =
    Codec.create(_.asJson.noSpaces, str => decode[T](str).right.get)

}
