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

import android.content.Context
import com.waz.db.{ColumnBuilders, Dao}
import com.waz.model.UploadAssetId
import com.waz.service.assets.UploadAssetStorage.UploadAssetDao
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{CachedStorage2, CirceJSONSupport, DbStorage2, InMemoryStorage2, ReactiveStorage2, ReactiveStorageImpl2, TrimmingLruCache}

import scala.concurrent.ExecutionContext

trait UploadAssetStorage extends ReactiveStorage2[UploadAssetId, UploadAsset]

class UploadAssetStorageImpl(context: Context, db: DB)(implicit ec: ExecutionContext)
    extends ReactiveStorageImpl2(
      new CachedStorage2(
        new DbStorage2(UploadAssetDao)(ec, db),
        new InMemoryStorage2[UploadAssetId, UploadAsset](new TrimmingLruCache(context, Fixed(8)))(ec)
      )(ec)
    )
    with UploadAssetStorage

object UploadAssetStorage {

  object UploadAssetDao
      extends Dao[UploadAsset, UploadAssetId]
      with ColumnBuilders[UploadAsset]
      with StorageCodecs
      with CirceJSONSupport {

    val Id             = asText(_.id)('_id, "PRIMARY KEY")
    val Source         = asText(_.localSource)('source)
    val Name           = text(_.name)('name)
    val Sha            = asBlob(_.sha)('sha)
    val Md5            = asBlob(_.md5)('md5)
    val Mime           = asText(_.mime)('mime)
    val Preview        = asText(_.preview)('preview)
    val Uploaded       = long(_.uploaded)('uploaded)
    val Size           = long(_.size)('size)
    val Retention      = asInt(_.retention)('retention)
    val Public         = bool(_.public)('public)
    val Encryption     = asText(_.encryption)('encryption)
    val EncryptionSalt = asTextOpt(_.encryptionSalt)('encryption_salt)
    val Details        = asText(_.details)('details)
    val UploadStatus   = asInt(_.status)('status)
    val AssetId        = asTextOpt(_.assetId)('asset_id)

    override val idCol = Id
    override val table = Table(
      "UploadAssets",
      Id,
      Source,
      Name,
      Sha,
      Md5,
      Mime,
      Preview,
      Uploaded,
      Size,
      Retention,
      Public,
      Encryption,
      EncryptionSalt,
      Details,
      UploadStatus,
      AssetId
    )

    override def apply(implicit cursor: DBCursor): UploadAsset =
      UploadAsset(Id,
        Source,
        Name,
        Sha,
        Md5,
        Mime,
        Preview,
        Uploaded,
        Size,
        Retention,
        Public,
        Encryption,
        EncryptionSalt,
        Details,
        UploadStatus,
        AssetId)

  }

}
