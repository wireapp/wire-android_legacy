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
package com.waz.sync.queue

import com.waz.api.SyncState
import com.waz.content.SyncStorage
import com.waz.model.SyncId
import com.waz.model.sync._
import com.waz.utils._

import scala.collection.{breakOut, mutable}

/**
 * Maintains a list of sync jobs with matching merge key.
 */
class SyncJobMerger(mergeKey: Any, storage: SyncStorage) {
  import SyncJobMerger._

  private[sync] val jobs = new mutable.HashMap[SyncId, SyncJob]

  def insert(job: SyncJob): Unit = {
    assert(job.mergeKey == mergeKey, s"Can only add jobs with matching merge key: $mergeKey, tried: $job")
    jobs.get(job.id).fold(jobs(job.id) = job)(update(_, job))
  }

  def remove(id: SyncId): Unit = jobs.remove(id)

  def merge(job: SyncJob): MergeResult[SyncJob] = {

    def merge(job: SyncJob, js: List[SyncJob]): MergeResult[SyncJob] = js match {
      case Nil =>
        jobs(job.id) = job
        Updated(job)
      case h :: tail =>
        h.merge(job) match {
          case Unchanged => merge(job, tail)
          case Updated(u) => merge(u, tail)
          case Merged(m) =>
            val res = mergeDependent(m, tail)
            jobs(m.id) = res
            Merged(res)
        }
    }

    jobs.remove(job.id) // remove if was already added
    merge(job, listJobs)
  }

  private def update(prev: SyncJob, updated: SyncJob): SyncJob = {
    assert(prev.id == updated.id)
    jobs(updated.id) = updated

    if (prev.state == SyncState.SYNCING && updated.state == SyncState.FAILED) {
      val merged = mergeDependent(updated, listJobs.dropWhile(_.timestamp <= updated.timestamp))
      if (merged != updated) {
        jobs(updated.id) = merged
        storage.add(merged)
      }
      merged
    } else
      updated
  }

  /**
   * Tries to merge all dependant jobs into this one.
   */
  private def mergeDependent(job: SyncJob, js: List[SyncJob]): SyncJob = {
    val childIds: Set[SyncId] = js.map(_.id)(breakOut)
    var toUpdate = Set.empty[SyncJob]
    var toRemove = Set.empty[SyncId]

    def merge(job: SyncJob, js: List[SyncJob]): SyncJob = js match {
      case Nil => job
      case h :: tail =>
        job.merge(h) match {
          case Unchanged => merge(job, tail)
          case Updated(u) =>
            toUpdate += u
            insert(u)
            merge(job, tail)
          case Merged(m) =>
            toRemove += h.id
            remove(h.id)
            // continue merging, make sure we don't end up with cyclic dependencies
            merge(m.copy(dependsOn = (m.dependsOn -- childIds - job.id).filterNot(hasDependency(_, job.id))), tail)
        }
    }

    returning(merge(job, js)) { _ =>
      toRemove foreach storage.remove
      toUpdate foreach storage.add
    }
  }

  /**
   * Check if job with given 'jobId' depends (including transitive) on 'dep'.
   */
  private def hasDependency(jobId: SyncId, dep: SyncId): Boolean = {
    jobId == dep || storage.get(jobId).fold(false) { job =>
      job.dependsOn(dep) || job.dependsOn.exists(hasDependency(_, dep))
    }
  }

  def isEmpty = jobs.isEmpty

  private def listJobs = jobs.values.toList.sortBy(_.timestamp)
}

object SyncJobMerger {

  sealed trait MergeResult[+A]
  case object Unchanged extends MergeResult[Nothing]

  /**
    * Updated leaves the second job in the queue, but with a modified request, because the first job contains a request
    * that overlaps part of the second request (where the first request can't handle all information). See the SyncUsers
    * for a good example
    */
  case class Updated[+A](req: A) extends MergeResult[A]

  /**
    * Merged removed the second job from the queue, that is, it gets "merged" into the first job. This merging may also
    * have an effect on the first job's request.
    */
  case class Merged[+A](result: A) extends MergeResult[A]
}



