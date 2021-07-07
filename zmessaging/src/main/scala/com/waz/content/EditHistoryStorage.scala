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
import com.waz.model.EditHistory.EditHistoryDao
import com.waz.model.{EditHistory, MessageId}
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils._
import com.waz.utils.crypto.ZSecureRandom
import org.threeten.bp.Instant

import scala.concurrent.duration._

trait EditHistoryStorage extends CachedStorage[MessageId, EditHistory]

/**
  * Edit history is only needed for short time to resolve race conditions when some message is edited on two devices at the same time.
  * We don't want to store it permanently, so will drop items older than 1 week.
  */
final class EditHistoryStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[MessageId, EditHistory](
    new TrimmingLruCache(context, Fixed(512)), storage)(EditHistoryDao, LogTag("EditHistoryStorage_Cached")
  ) with EditHistoryStorage {

  import EditHistoryStorage._
  import Threading.Implicits.Background

  // run db cleanup on each app start, will wait a bit to not execute it too soon
  CancellableFuture.delayed((5 + ZSecureRandom.nextInt(10)).seconds) {
    storage { EditHistoryDao.deleteOlder(Instant.now.minus(EditExpiryTime))(_) }
  }
}

object EditHistoryStorage {
  val EditExpiryTime = 7.days
}
