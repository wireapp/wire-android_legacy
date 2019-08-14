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
package com.waz.sync.client

import java.io.{BufferedOutputStream, File, FileOutputStream, InputStream}
import java.security.{DigestOutputStream, MessageDigest}

import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ProgressIndicator.{Callback, ProgressData}
import com.waz.cache.{CacheEntry, CacheService, Expiration, LocalData}
import com.waz.model.{Mime, _}
import com.waz.utils.crypto.AESUtils
import com.waz.utils.{IoUtils, JsonDecoder, JsonEncoder}
import com.waz.znet2.http.HttpClient.AutoDerivationOld._
import com.waz.znet2.http.HttpClient.dsl._
import com.waz.znet2.http.HttpClient.{Progress, ProgressCallback}
import com.waz.znet2.http.MultipartBodyMixed.Part
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.concurrent.duration._

trait AssetClient {
  import com.waz.sync.client.AssetClient._

  //TODO Request should be constructed inside "*Client" classes
  def loadAsset[T: RequestSerializer](
      req: Request[T],
      key: Option[AESKey] = None,
      sha: Option[Sha256] = None,
      callback: Callback
  ): ErrorOrResponse[CacheEntry]

  //TODO Add callback parameter. https://github.com/wireapp/wire-android-sync-engine/issues/378
  def uploadAsset(metadata: Metadata, data: LocalData, mime: Mime): ErrorOrResponse[UploadResponse]

}

class AssetClientImpl(cacheService: CacheService)
                     (implicit
                      urlCreator: UrlCreator,
                      client: HttpClient,
                      authRequestInterceptor: RequestInterceptor = RequestInterceptor.identity)
  extends AssetClient {

  import AssetClient._

  private implicit def fileWithShaBodyDeserializer: RawBodyDeserializer[FileWithSha] =
    RawBodyDeserializer.create { body =>
      val tempFile = File.createTempFile("http_client_download", null)
      val out = new DigestOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)),
        MessageDigest.getInstance("SHA-256"))
      IoUtils.copy(body.data(), out)
      FileWithSha(tempFile, Sha256(out.getMessageDigest.digest()))
    }

  private def cacheEntryBodyDeserializer(key: Option[AESKey], sha: Option[Sha256]): RawBodyDeserializer[CacheEntry] =
    RawBodyDeserializer.create { body =>
      val entry = cacheService.createManagedFile(key)
      val out = new DigestOutputStream(new BufferedOutputStream(new FileOutputStream(entry.cacheFile)),
                                       MessageDigest.getInstance("SHA-256"))
      IoUtils.copy(body.data(), out)
      if (sha.exists(_ != Sha256(out.getMessageDigest.digest()))) {
        throw new IllegalArgumentException(
          s"SHA256 not match. \nExpected: $sha \nCurrent: ${Sha256(out.getMessageDigest.digest())}"
        )
      }

      entry
    }

  private def localDataRawBodySerializer(mime: Mime): RawBodySerializer[LocalData] =
    RawBodySerializer.create { data =>
      RawBody(mediaType = Some(mime.str), () => data.inputStream, dataLength = Some(data.length))
    }

  override def loadAsset[T: RequestSerializer](request: Request[T],
                                               key: Option[AESKey] = None,
                                               sha: Option[Sha256] = None,
                                               callback: Callback): ErrorOrResponse[CacheEntry] = {
    val progressCallback: ProgressCallback = new ProgressCallback {
      override def updated(progress: Long, total: Option[Long]): Unit = callback(ProgressData(progress, total))
    }
    implicit val bodyDeserializer: RawBodyDeserializer[CacheEntry] = cacheEntryBodyDeserializer(key, sha)

    request
      .withDownloadCallback(progressCallback)
      .withResultType[CacheEntry]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def uploadAsset(metadata: Metadata, data: LocalData, mime: Mime): ErrorOrResponse[UploadResponse] = {
    implicit val rawBodySerializer: RawBodySerializer[LocalData] = localDataRawBodySerializer(mime)
    Request
      .Post(
        relativePath = AssetsV3Path,
        body = MultipartBodyMixed(Part(metadata), Part(data, Headers("Content-MD5" -> md5(data))))
      )
      .withResultType[UploadResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  private implicit def RawAssetRawBodySerializer: RawBodySerializer[RawAsset] =
    RawBodySerializer.create { asset =>
      RawBody(mediaType = Some(asset.mime.str), asset.data, dataLength = asset.dataLength)
    }
}

object AssetClient {

  case class FileWithSha(file: File, sha256: Sha256)

  case class RawAsset(mime: Mime, data: () => InputStream, dataLength: Option[Long])

  implicit val DefaultExpiryTime: Expiration = 1.hour

  val AssetsV3Path = "/assets/v3"

  sealed abstract class Retention(val value: String)
  object Retention {
    case object Eternal                 extends Retention("eternal") //Only used for profile pics currently
    case object EternalInfrequentAccess extends Retention("eternal-infrequent_access")
    case object Persistent              extends Retention("persistent")
    case object Expiring                extends Retention("expiring")
    case object Volatile                extends Retention("volatile")
  }

  case class Metadata(public: Boolean = false, retention: Retention = Retention.Persistent)

  object Metadata {
    implicit val jsonEncoder: JsonEncoder[Metadata] = JsonEncoder.build[Metadata] { metadata => o =>
      o.put("public", metadata.public)
      o.put("retention", metadata.retention.value)
    }
  }

  case class UploadResponse(rId: RAssetId, expires: Option[Instant], token: Option[AssetToken])

  case object UploadResponse {
    implicit val jsonDecoder: JsonDecoder[UploadResponse] = new JsonDecoder[UploadResponse] {
      import JsonDecoder._
      override def apply(implicit js: JSONObject): UploadResponse =
        UploadResponse(RAssetId('key), decodeOptISOInstant('expires), decodeOptString('token).map(AssetToken))
    }
  }

  def getAssetPath(rId: RAssetId, otrKey: Option[AESKey], conv: Option[RConvId]): String =
    (conv, otrKey) match {
      case (None, _)          => s"/assets/v3/${rId.str}"
      case (Some(c), None)    => s"/conversations/${c.str}/assets/${rId.str}"
      case (Some(c), Some(_)) => s"/conversations/${c.str}/otr/assets/${rId.str}"
    }

  /**
    * Computes base64 encoded md5 sum of image data.
    */
  def md5(data: LocalData): String = md5(data.inputStream)

  def md5(is: InputStream): String = AESUtils.base64(IoUtils.md5(is))

}
