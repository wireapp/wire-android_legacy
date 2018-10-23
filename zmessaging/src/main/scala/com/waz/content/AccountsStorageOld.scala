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
import com.waz.ZLog.ImplicitTag._
import com.waz.model.AccountData.AccountDataDao
import com.waz.model.AccountDataOld.AccountDataOldDao
import com.waz.model._
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.wrappers.DB
import com.waz.utils.{CachedStorage, CachedStorage2, CachedStorageImpl, DbStorage2, InMemoryStorage2, Storage2, TrimmingLruCache}

import scala.concurrent.ExecutionContext

trait AccountStorage2 extends Storage2[UserId, AccountData]
class AccountStorageImpl2(context: Context, db: DB, ec: ExecutionContext)
  extends CachedStorage2[UserId, AccountData](
    new DbStorage2(AccountDataDao)(ec, db),
    new InMemoryStorage2[UserId, AccountData](new TrimmingLruCache(context, Fixed(8)), AccountDataDao.idExtractor)(ec)
  )(ec) with AccountStorage2

trait AccountStorage extends CachedStorage[UserId, AccountData]
class AccountStorageImpl(context: Context, storage: Database) extends CachedStorageImpl[UserId, AccountData](new TrimmingLruCache(context, Fixed(8)), storage)(AccountDataDao) with AccountStorage

trait AccountsStorageOld extends CachedStorage[AccountId, AccountDataOld]
class AccountsStorageOldImpl(context: Context, storage: Database) extends CachedStorageImpl[AccountId, AccountDataOld](new TrimmingLruCache(context, Fixed(8)), storage)(AccountDataOldDao) with AccountsStorageOld
