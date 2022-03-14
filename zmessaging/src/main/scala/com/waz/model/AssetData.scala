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

import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetStatus.{UploadCancelled, UploadDone}
import com.waz.model.GenericContent.Asset.EncryptionAlgorithm
import com.waz.utils.JsonDecoder.{apply => _, opt => _}
import com.waz.utils._
import com.waz.utils.crypto.AESUtils
import com.waz.utils.wrappers.{DBCursor, URI}
import org.json.JSONObject

final case class AssetData(override val id: AssetId               = AssetId(),
                           mime:            Mime                  = Mime.Unknown,
                           sizeInBytes:     Long                  = 0L,
                           status:          AssetStatus           = AssetStatus.UploadNotStarted,
                           remoteId:        Option[RAssetId]      = None,
                           token:           Option[AssetToken]    = None,
                           domain:          Option[Domain]        = None,
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
  private lazy val data64 = data.map(AESUtils.base64)

  lazy val remoteData: Option[RemoteData] = (remoteId, token, domain, otrKey, sha, encryption) match {
    case (None, None, None, None, None, None) =>
      None
    case _ =>
      Some(RemoteData(remoteId, token, domain, otrKey, sha, encryption))
  }

  val dimensions: Dim2 = this match {
    case WithDimensions(dim) => dim
    case _ => Dim2(0, 0)
  }

  def copyWithRemoteData(remoteData: RemoteData): AssetData = {
    val res = copy(
      remoteId  = remoteData.remoteId,
      token     = remoteData.token,
      domain    = remoteData.domain,
      otrKey    = remoteData.otrKey,
      sha       = remoteData.sha256
    )
    res.copy(status = res.remoteData.fold(res.status)(_ => if (res.status != UploadCancelled) UploadDone else res.status))
  }
}

object AssetData {

  def decodeData(data64: String): Array[Byte] = AESUtils.base64(data64)

  private def isExternalUri(uri: URI): Boolean = Option(uri.getScheme).forall(_.startsWith("http"))

  //simplify handling remote data from asset data
  final case class RemoteData(remoteId:   Option[RAssetId]            = None,
                              token:      Option[AssetToken]          = None,
                              domain:     Option[Domain]              = None,
                              otrKey:     Option[AESKey]              = None,
                              sha256:     Option[Sha256]              = None,
                              encryption: Option[EncryptionAlgorithm] = None
                             )

  val Empty: AssetData = AssetData()

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

  private val MaxTeamAssetSizeInBytes   = 100L * 1024 * 1024
  private val MaxNoTeamAssetSizeInBytes = 25L  * 1024 * 1024

  def maxAssetSizeInBytes(isTeam: Boolean): Long =
    if (isTeam) MaxTeamAssetSizeInBytes
    else MaxNoTeamAssetSizeInBytes

  final case class ProcessingTaskKey(id: GeneralAssetId) extends AnyVal

  final case class UploadTaskKey(id: GeneralAssetId) extends AnyVal

  implicit object AssetDataDao extends Dao[AssetData, AssetId] {
    val Id    = id[AssetId]('_id, "PRIMARY KEY").apply(_.id)
    val Asset = text[AssetType]('asset_type, _.name, AssetType.valueOf)(_ => AssetType.Empty)
    val Data = text('data)(JsonEncoder.encodeString(_))

    override val idCol = Id
    override val table = Table("Assets", Id, Asset, Data)

    override def apply(implicit cursor: DBCursor): AssetData =
      JsonDecoder.decode(Data)(AssetDataDecoder)
  }

  implicit lazy val AssetDataEncoder: JsonEncoder[AssetData] = new JsonEncoder[AssetData] {
    override def apply(data: AssetData): JSONObject = JsonEncoder { o =>
      o.put("id",           data.id.str)
      o.put("mime",         data.mime.str)
      o.put("sizeInBytes",  data.sizeInBytes)
      o.put("status",       JsonEncoder.encode(data.status))
      data.remoteId     foreach (v => o.put("remoteId",     v.str))
      data.token        foreach (v => o.put("token",        v.str))
      data.domain       foreach (v => o.put("domain",       v.str))
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
        decodeOptString('domain).map(Domain(_)),
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

}

final case class AssetToken(str: String) extends AnyVal

object AssetToken extends (String => AssetToken)
