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
package com.waz.model

import com.waz.content.WireContentProvider
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.AssetStatus.{UploadCancelled, UploadDone}
import com.waz.model.GenericContent.Asset.EncryptionAlgorithm
import com.waz.model.otr.SignalingKey
import com.waz.service.ZMessaging
import com.waz.sync.client.AssetClient
import com.waz.utils.JsonDecoder.{apply => _, opt => _}
import com.waz.utils._
import com.waz.utils.crypto.AESUtils
import com.waz.utils.wrappers.{DBCursor, URI}
import org.json.JSONObject
import org.threeten.bp.Duration

import scala.util.Try

final case class AssetData(override val id: AssetId               = AssetId(),
                           mime:            Mime                  = Mime.Unknown,
                           sizeInBytes:     Long                  = 0L,
                           status:          AssetStatus           = AssetStatus.UploadNotStarted,
                           remoteId:        Option[RAssetId]      = None,
                           token:           Option[AssetToken]    = None,
                           otrKey:          Option[AESKey]        = None,
                           sha:             Option[Sha256]        = None,
                           encryption:      Option[EncryptionAlgorithm] = None,
                           name:            Option[String]        = None,
                           previewId:       Option[AssetId]       = None,
                           metaData:        Option[AssetMetaData] = None,
                           source:          Option[URI]           = None,
                           proxyPath:       Option[String]        = None,
                           //data only used for temporary caching and legacy reasons - shouldn't be stored in AssetsStorage where possible
                           data:            Option[Array[Byte]]   = None
                          ) extends Identifiable[AssetId] {

  import AssetData._

  lazy val size: Long = data.fold(sizeInBytes)(_.length)

  //be careful when accessing - can be expensive
  lazy val data64 = data.map(AESUtils.base64)

  lazy val fileExtension = mime.extension

  lazy val remoteData: Option[RemoteData] = (remoteId, token, otrKey, sha, encryption) match {
    case (None, None, None, None, None) => Option.empty[RemoteData]
    case _ => Some(RemoteData(remoteId, token, otrKey, sha, encryption))
  }

  lazy val cacheKey: CacheKey = (proxyPath, source) match {
    case (Some(proxy), _)                                          => CacheKey(proxy)
    case (_, Some(uri)) if uri.getPath != AssetClient.UnsplashPath => CacheKey.fromUri(uri)
    case _                                                         => CacheKey.fromAssetId(id)
  }

  lazy val isDownloadable = this match {
    case WithRemoteData(_)  => true
    case WithExternalUri(_) => true
    case WithProxy(_)       => true
    case _                  => false
  }

  val (isImage, isVideo, isAudio) = this match {
    case IsImage() => (true, false, false)
    case IsVideo() => (false, true, false)
    case IsAudio() => (false, false, true)
    case _         => (false, false, false)
  }

  val tag = this match {
    case IsImageWithTag(t) => t
    case _ => Image.Tag.Empty
  }

  val dimensions = this match {
    case WithDimensions(dim) => dim
    case _ => Dim2(0, 0)
  }

  val width = dimensions.width
  val height = dimensions.height

  def copyWithRemoteData(remoteData: RemoteData) = {
    val res = copy(
      remoteId  = remoteData.remoteId,
      token     = remoteData.token,
      otrKey    = remoteData.otrKey,
      sha       = remoteData.sha256
    )
    res.copy(status = res.remoteData.fold(res.status)(_ => if (res.status != UploadCancelled) UploadDone else res.status))
  }
}

object AssetData {

  def decodeData(data64: String): Array[Byte] = AESUtils.base64(data64)

  def cacheKeyFrom(uri: URI): CacheKey = WireContentProvider.CacheUri.unapply(ZMessaging.context)(uri).getOrElse(CacheKey(uri.toString))

  def isExternalUri(uri: URI): Boolean = Option(uri.getScheme).forall(_.startsWith("http"))

  //simplify handling remote data from asset data
  case class RemoteData(remoteId:   Option[RAssetId]            = None,
                        token:      Option[AssetToken]          = None,
                        otrKey:     Option[AESKey]              = None,
                        sha256:     Option[Sha256]              = None,
                        encryption: Option[EncryptionAlgorithm] = None
                       )

  //needs to be def to create new id each time. "medium" tag ensures it will not be ignored by MessagesService
  def newImageAsset(id: AssetId = AssetId(), tag: Image.Tag) = AssetData(id = id, metaData = Some(AssetMetaData.Image(Dim2(0, 0), tag)))

  def newImageAssetFromUri(id: AssetId = AssetId(), tag: Image.Tag = Tag.Medium, uri: URI) = AssetData(id = id, metaData = AssetMetaData.Image(ZMessaging.context, uri, tag), source = Some(uri))

  val Empty = AssetData()

  object WithRemoteData {
    def unapply(asset: AssetData): Option[RemoteData] = asset.remoteData
  }

  object WithExternalUri {
    def unapply(asset: AssetData): Option[URI] = asset.source.filter(isExternalUri)
  }

  object WithProxy {
    def unapply(asset: AssetData): Option[String] = asset.proxyPath
  }

  object WithMetaData {
    def unapply(asset: AssetData): Option[AssetMetaData] = asset.metaData
  }

  object IsImage {
    def unapply(asset: AssetData): Boolean = Mime.Image.unapply(asset.mime) || (asset.metaData match {
      case Some(AssetMetaData.Image(dims, tag)) => true
      case _ => false
    })
  }

  object IsVideo {
    def unapply(asset: AssetData): Boolean = Mime.Video.unapply(asset.mime) || (asset.metaData match {
      case Some(AssetMetaData.Video(_, _)) => true
      case _ => false
    })
  }

  object IsAudio {
    def unapply(asset: AssetData): Boolean = Mime.Audio.unapply(asset.mime) || (asset.metaData match {
      case Some(AssetMetaData.Audio(_, _)) => true
      case _ => false
    })
  }

  object WithDimensions {
    def unapply(asset: AssetData): Option[Dim2] = asset.metaData match {
      case Some(AssetMetaData.HasDimensions(dimensions)) => Some(dimensions)
      case _ => None
    }
  }

  object IsImageWithTag {
    def unapply(asset: AssetData): Option[Image.Tag] = asset.metaData match {
      case Some(AssetMetaData.Image(_, tag)) => Some(tag)
      case _ => None
    }
  }

  object WithDuration {
    def unapply(asset: AssetData): Option[Duration] = asset.metaData match {
      case Some(AssetMetaData.HasDuration(duration)) => Some(duration)
      case _ => None
    }
  }

  object WithPreview {
    def unapply(asset: AssetData): Option[AssetId] = asset.previewId
  }

  object WithRemoteId {
    def unapply(asset: AssetData): Option[RAssetId] = asset.remoteId
  }

  object WithStatus {
    def unapply(asset: AssetData): Option[AssetStatus] = Some(asset.status)
  }

  object WithSource {
    def unapply(asset: AssetData): Option[URI] = asset.source
  }

  private val MaxTeamAssetSizeInBytes   = 100L * 1024 * 1024
  private val MaxNoTeamAssetSizeInBytes = 25L  * 1024 * 1024

  def maxAssetSizeInBytes(isTeam: Boolean): Long =
    if (isTeam) MaxTeamAssetSizeInBytes
    else MaxNoTeamAssetSizeInBytes

  case class ProcessingTaskKey(id: GeneralAssetId)

  case class UploadTaskKey(id: GeneralAssetId)

  implicit object AssetDataDao extends Dao[AssetData, AssetId] {
    val Id    = id[AssetId]('_id, "PRIMARY KEY").apply(_.id)
    val Asset = text[AssetType]('asset_type, _.name, AssetType.valueOf)(_ => AssetType.Empty)
    val Data = text('data)(JsonEncoder.encodeString(_))

    override val idCol = Id
    override val table = Table("Assets", Id, Asset, Data)

    override def apply(implicit cursor: DBCursor): AssetData = {
      val tpe: AssetType = Asset
      tpe match {
        case AssetType.Image => JsonDecoder.decode(Data)(ImageAssetDataDecoder)
        case AssetType.Any   => JsonDecoder.decode(Data)(AnyAssetDataDecoder)
        case _               => JsonDecoder.decode(Data)(AssetDataDecoder)
      }
    }
  }

  implicit lazy val AssetDataEncoder: JsonEncoder[AssetData] = new JsonEncoder[AssetData] {
    override def apply(data: AssetData): JSONObject = JsonEncoder { o =>
      o.put("id",           data.id.str)
      o.put("mime",         data.mime.str)
      o.put("sizeInBytes",  data.sizeInBytes)
      o.put("status",       JsonEncoder.encode(data.status))
      data.remoteId     foreach (v => o.put("remoteId",     v.str))
      data.token        foreach (v => o.put("token",        v.str))
      data.otrKey       foreach (v => o.put("otrKey",       v.str))
      data.sha          foreach (v => o.put("sha256",       v.str))
      data.name         foreach (v => o.put("name",         v))
      data.previewId    foreach (v => o.put("preview",      v.str))
      data.metaData     foreach (v => o.put("metaData",     JsonEncoder.encode(v)))
      data.source       foreach (v => o.put("source",       v.toString))
      data.proxyPath    foreach (v => o.put("proxyPath",    v))
      data.data64       foreach (v => o.put("data64",       v))
    }
  }

  lazy val AssetDataDecoder: JsonDecoder[AssetData] = new JsonDecoder[AssetData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): AssetData = {
      AssetData(
        'id,
        Mime('mime),
        'sizeInBytes,
        JsonDecoder[AssetStatus]('status),
        decodeOptRAssetId('remoteId),
        decodeOptString('token).map(AssetToken(_)),
        decodeOptString('otrKey).map(AESKey(_)),
        decodeOptString('sha256).map(Sha256(_)),
        decodeOptInt('encryption).map(EncryptionAlgorithm(_)),
        'name,
        'preview,
        opt[AssetMetaData]('metaData),
        decodeOptString('source).map(URI.parse),
        'proxyPath,
        decodeOptString('data).map(decodeData)
      )
    }
  }


  //TODO Dean: remove after v2 transition period
  //This decoder is used to decode the old ImageAssetData type from SQL storage. We just need enough information to re-download it again
  lazy val ImageAssetDataDecoder: JsonDecoder[AssetData] = new JsonDecoder[AssetData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): AssetData = {
      //verbose(s"decoding ImageAssetData: $js")
      val id = decodeId[AssetId]('id)

      Try(js.getJSONArray("versions")).toOption.flatMap { arr =>
        Seq.tabulate(arr.length())(arr.getJSONObject).map { obj =>
          //verbose(s"applying ImageDataDecoder to $obj")
          ImageDataDecoder.apply(obj)
        }.collectFirst {
          case a@AssetData.IsImageWithTag(Image.Tag.Medium) => a.copy(id = id)
        }
      }.getOrElse(AssetData(id))
    }
  }

  //TODO Dean: remove after v2 transition period
  //This decoder is used to decode the old ImageData type from SQL storage. We just need enough information to re-download it again
  lazy val ImageDataDecoder: JsonDecoder[AssetData] = new JsonDecoder[AssetData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): AssetData = {
      //verbose(s"decoding ImageData: $js")
      val otrKey = js.opt("otrKey") match {
        case o: JSONObject => Try(implicitly[JsonDecoder[SignalingKey]].apply(o).encKey).toOption
        case str: String => Some(AESKey(str))
        case _ => None
      }

      AssetData(
        mime = Mime('mime),
        sizeInBytes = 'size,
        metaData = Some(AssetMetaData.Image(Dim2('width, 'height), Image.Tag('tag))),
        source = decodeOptString('url).map(URI.parse),
        proxyPath = decodeOptString('proxyPath)
      ).copyWithRemoteData(RemoteData('remoteId, None, otrKey, decodeOptString('sha256).map(Sha256(_))))
    }
  }

  //TODO Dean: remove after v2 transition period
  //This decoder is used to decode the old AnyAssetData type from SQL storage. We just need enough information to re-download it again
  implicit lazy val AnyAssetDataDecoder: JsonDecoder[AssetData] = new JsonDecoder[AssetData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): AssetData = {
      //verbose(s"decoding AnyAssetData: $js")

      val remoteData = decodeOptObject('key)(js.getJSONObject("status")).map { key =>
        val remoteId = decodeOptRAssetId('remoteId)(key).orElse(decodeOptRAssetId('remoteKey)(key))
        val token = decodeOptString('token)(key).map(AssetToken)
        val otrKey = decodeOptString('otrKey)(key).map(AESKey(_))
        val sha = decodeOptString('sha256)(key).map(Sha256(_))
        RemoteData(remoteId, token, otrKey, sha)
      }

      val mime = Mime('mime) match {
        case Mime.Unknown => Mime('mimeType)
        case m: Mime => m
      }

      val source = mime match {
        case Mime.Audio() => None //we don't want the unencoded url stored previously - use id as cache key instead
        case _ => decodeOptString('source).map(URI.parse)
      }

      val asset = AssetData(
        decodeAssetId('id),
        mime = mime,
        sizeInBytes = 'sizeInBytes,
        name = 'name,
        source = source
      )

      remoteData.map(asset.copyWithRemoteData).getOrElse(asset)
    }
  }

}

case class AssetToken(str: String) extends AnyVal

object AssetToken extends (String => AssetToken)


