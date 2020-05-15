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
package com.waz.content
import android.content.Context
import com.waz.db.{ColumnBuilders, Dao, Table}
import com.waz.service.PropertyKey
import com.waz.service.assets.StorageCodecs
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{CachedStorage2, DbStorage2, Identifiable, InMemoryStorage2, ReactiveStorage2, ReactiveStorageImpl2, TrimmingLruCache}

import scala.concurrent.ExecutionContext

case class PropertyValue(key: PropertyKey, value: String) extends Identifiable[PropertyKey] {
  override val id: PropertyKey = key
}

trait PropertiesStorage extends ReactiveStorage2[PropertyKey, PropertyValue]

class PropertiesStorageImpl(implicit context: Context, db: DB, ec: ExecutionContext) extends ReactiveStorageImpl2[PropertyKey, PropertyValue](
    new CachedStorage2(
      new DbStorage2(PropertiesDao),
      new InMemoryStorage2(new TrimmingLruCache(context, Fixed(8)))))
  with PropertiesStorage


object PropertiesDao extends Dao[PropertyValue, PropertyKey] with ColumnBuilders[PropertyValue] with StorageCodecs {

  val Id    = asText(_.key)('key, "PRIMARY KEY")
  val Value = asText(_.value)('value)

  override  val idCol: PropertiesDao.Column[PropertyKey] = Id
  override  val table: Table[PropertyValue] = Table("Properties", Id, Value)
  override def apply(implicit c:  DBCursor): PropertyValue = PropertyValue(Id, Value)
}
