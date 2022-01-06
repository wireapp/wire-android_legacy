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
import com.waz.model._
import com.waz.service.assets.AssetStorageImpl.AssetDao
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{CachedStorage2, CirceJSONSupport, DbStorage2, InMemoryStorage2, ReactiveStorage2, ReactiveStorageImpl2, TrimmingLruCache}
import io.circe.Decoder

import scala.concurrent.ExecutionContext

trait AssetStorage extends ReactiveStorage2[AssetId, Asset]

final class AssetStorageImpl(context: Context, db: DB, ec: ExecutionContext)
  extends ReactiveStorageImpl2(
    new CachedStorage2[AssetId, Asset](
      new DbStorage2(AssetDao)(ec, db),
      new InMemoryStorage2[AssetId, Asset](new TrimmingLruCache(context, Fixed(1024)))(ec)
    )(ec)
  )
  with AssetStorage

object AssetStorageImpl {

  object AssetDao
      extends Dao[Asset, AssetId]
      with ColumnBuilders[Asset]
      with StorageCodecs
      with CirceJSONSupport {

    val dec = Decoder[LocalSource]

    val Id         = asText(_.id)('_id, "PRIMARY KEY")
    val Token      = asTextOpt(_.token)('token)
    val Domain     = asTextOpt(_.domain)('domain)
    val Name       = text(_.name)('name)
    val Encryption = asText(_.encryption)('encryption)
    val Mime       = asText(_.mime)('mime)
    val Sha        = asBlob(_.sha)('sha)
    val Size       = long(_.size)('size)
    val Source     = asTextOpt(_.localSource)('source)
    val Preview    = asTextOpt(_.preview)('preview)
    val Details    = asText(_.details)('details)

    override val idCol = Id
    override val table =
      Table("Assets2", Id, Token, Domain, Name, Encryption, Mime, Sha, Size, Source, Preview, Details)

    override def apply(implicit cursor: DBCursor): Asset =
      Asset(Id, Token, Domain, Sha, Mime, Encryption, Source, Preview, Name, Size, Details)
  }
}
