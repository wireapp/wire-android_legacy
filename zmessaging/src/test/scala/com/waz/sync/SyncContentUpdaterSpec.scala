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
package com.waz.sync

import com.waz.content.Database
import com.waz.log.BasicLogging.LogTag
import com.waz.model.sync.SyncJob
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.sync.SyncRequest.PostOpenGraphMeta
import com.waz.model.{ConvId, MessageId, RemoteInstant, SyncId}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.queue.SyncContentUpdater.StaleJobTimeout
import com.waz.sync.queue.{SyncContentUpdater, SyncContentUpdaterImpl}
import com.wire.signals.CancellableFuture
import com.waz.utils.wrappers.DB

class SyncContentUpdaterSpec extends AndroidFreeSpec {
  private val db = mock[Database]

  scenario("Returning stale sync jobs") {

    val staleJob = SyncJob(SyncId(), PostOpenGraphMeta(ConvId(), MessageId(), RemoteInstant(clock.instant())), priority = Priority.Low, timestamp = System.currentTimeMillis() - (StaleJobTimeout.toMillis + 10000L))
    val nonStaleJob = SyncJob(SyncId(), PostOpenGraphMeta(ConvId(), MessageId(), RemoteInstant(clock.instant())), priority = Priority.Low, timestamp = System.currentTimeMillis() - (StaleJobTimeout.toMillis - 10000L))

    val savedJobs = Vector(
      staleJob,
      nonStaleJob
    )

    (db.apply[Vector[SyncJob]] (_: DB => Vector[SyncJob])(_: LogTag)).expects(*, *).returning(CancellableFuture.successful(savedJobs))
    val updater = getUpdater

    result(updater.getSyncJob(staleJob.id))    shouldEqual None
    result(updater.getSyncJob(nonStaleJob.id)) shouldEqual Some(nonStaleJob)
  }

  def getUpdater: SyncContentUpdater = {
    new SyncContentUpdaterImpl(db)
  }

}
