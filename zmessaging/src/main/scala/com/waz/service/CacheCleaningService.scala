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
package com.waz.service

import java.lang.System._

import com.waz.cache.CacheService
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.LastCacheCleanup
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.wire.signals.EventContext

import scala.concurrent.Future
import scala.concurrent.duration._

class CacheCleaningService(cache: CacheService, prefs: GlobalPreferences) extends DerivedLogTag {
  import CacheCleaningService._
  import Threading.Implicits.Background

  CancellableFuture.delayed(1.minute) { requestDeletionOfExpiredCacheEntries() }

  def requestDeletionOfExpiredCacheEntries(): Future[Unit] = {
    prefs.preference(LastCacheCleanup).mutate { lastSync =>
      if ((currentTimeMillis() - lastSync).millis > CleanupInterval) {
        debug(l"at least one week expired since last cache cleaning, cleaning now...")
        cache.deleteExpired()
        currentTimeMillis()
      } else {
        lastSync
      }
    }
  }
}

object CacheCleaningService {
  val CleanupInterval = 7.days
}
