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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE.{toLogHelper, verbose}
import com.waz.model.SyncId
import com.waz.model.sync.SyncJob
import com.waz.model.sync.SyncJob.SyncJobDao
import com.waz.utils.SerialProcessingQueue
import com.wire.signals.SourceStream

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.duration._

/**
 * Keeps all sync jobs in memory, maintains reverse dependencies list and persists changes to db.
 *
 * Warning - this is not thread safe
 */
class SyncStorage(db: Database, jobs: Seq[SyncJob]) extends DerivedLogTag {
  import SyncStorage._

  private val jobsMap = new mutable.HashMap[SyncId, SyncJob]

  val onAdded   = new SourceStream[SyncJob]
  val onUpdated = new SourceStream[(SyncJob, SyncJob)] // (prev, updated)
  val onRemoved = new SourceStream[SyncJob]

  private val saveQueue = new SerialProcessingQueue[SyncId]({ ids =>
    val tag = UUID.randomUUID()
    verbose(l"SSM5<$tag> SyncStorage serial processing queue processor called for ids: $ids")
    val toAdd = new mutable.HashMap[SyncId, SyncJob]
    val toDelete = new mutable.HashSet[SyncId]
    ids.foreach { id =>
      verbose(l"SSM5<$tag> processing for id $id")
      jobsMap.get(id) match {
        case Some(job) =>
          verbose(l"SSM5<$tag> found job $id")
          toAdd += (id -> job)
          toDelete -= job.id
        case None =>
          verbose(l"SSM5<$tag> job not found! $id")
          toDelete += id
          toAdd -= id
      }
    }
    db.withTransaction { implicit db =>
      verbose(l"SSM5<$tag> saving to db... (deleting: $toDelete, adding: ${toAdd.keySet})")
      SyncJobDao.deleteEvery(toDelete)
      SyncJobDao.insertOrReplace(toAdd.values)
    }
  }, "SyncStorageSaveQueue")

  jobsMap ++= jobs map { job => job.id -> job }

  def add(job: SyncJob): SyncJob = {
    jobsMap.get(job.id) match {
      case Some(prev) => update(prev, job)
      case None =>
        save(job)
        onAdded ! job
    }
    job
  }

  def get(id: SyncId): Option[SyncJob] = jobsMap.get(id)

  def remove(id: SyncId) = {
    jobsMap.remove(id) foreach { onRemoved ! _ }
    saveQueue ! id
  }

  def getJobs: Iterable[SyncJob] = jobsMap.values.toVector

  def update(id: SyncId)(updater: SyncJob => SyncJob): Option[SyncJob] =
    jobsMap.get(id) map { prev =>
      val updated = updater(prev)
      if (updated != prev) {
        assert(updated.id == prev.id, s"update should not change job id ($prev, $updated)")
        assert(updated.mergeKey == prev.mergeKey, s"update should not change the merge key ($prev, $updated)")

        update(prev, updated)
        updated
      } else
        updated
    }

  private def update(prev: SyncJob, updated: SyncJob) = {
    save(updated)
    onUpdated ! (prev, updated)
  }

  private def save(job: SyncJob) = {
    jobsMap.put(job.id, job)
    saveQueue ! job.id
  }
}

object SyncStorage {
  val SaveDelay = 500.millis
}
