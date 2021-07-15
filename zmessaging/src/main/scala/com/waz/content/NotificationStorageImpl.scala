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
import com.waz.log.BasicLogging.LogTag
import com.waz.model.NotificationData.NotificationDataDao
import com.waz.model.{NotId, NotificationData}
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

trait NotificationStorage extends CachedStorage[NotId, NotificationData]

final class NotificationStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[NotId, NotificationData](
    new TrimmingLruCache(context, Fixed(128)), storage)(NotificationDataDao, LogTag("NotificationStorage")
  ) with NotificationStorage
