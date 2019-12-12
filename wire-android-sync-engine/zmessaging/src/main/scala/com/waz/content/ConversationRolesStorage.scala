/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationRoleAction.ConversationRoleActionDao
import com.waz.model.{ConvId, ConversationRoleAction}
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

trait ConversationRolesStorage extends CachedStorage[(String, String, ConvId), ConversationRoleAction]

class ConversationRolesStorageImpl(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[(String, String, ConvId), ConversationRoleAction](
    new TrimmingLruCache(context, Fixed(1024)), storage)(ConversationRoleActionDao, LogTag("ConversationRolesStorage_Cached")
  ) with ConversationRolesStorage with DerivedLogTag

