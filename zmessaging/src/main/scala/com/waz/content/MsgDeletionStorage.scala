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
import com.waz.model.MsgDeletion.MsgDeletionDao
import com.waz.model.{MessageId, MsgDeletion}
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils._
import com.waz.utils.crypto.ZSecureRandom
import org.threeten.bp.Instant

import scala.concurrent.duration._


trait MsgDeletionStorage extends CachedStorage[MessageId, MsgDeletion]
/**
  * Deletion history is only stored for short time to handle partial message updates (assets, link preview).
  * We need that to discard new versions of previously deleted messages.
  * We don't want to store it permanently, so will drop items older than 2 weeks.
  */
final class MsgDeletionStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[MessageId, MsgDeletion](
    new TrimmingLruCache(context, Fixed(512)), storage)(MsgDeletionDao, LogTag("MsgDeletionStorage_Cached")
  ) with MsgDeletionStorage {

  import MsgDeletionStorage._
  import Threading.Implicits.Background

  // run db cleanup on each app start, will wait a bit to not execute it too soon
  CancellableFuture.delayed((5 + ZSecureRandom.nextInt(10)).seconds) {
    storage { MsgDeletionDao.deleteOlder(Instant.now.minus(DeletionExpiryTime))(_) }
  }
}

object MsgDeletionStorage {
  val DeletionExpiryTime = 14.days
}
