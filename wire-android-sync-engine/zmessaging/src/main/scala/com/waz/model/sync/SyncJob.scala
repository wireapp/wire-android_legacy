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
package com.waz.model.sync

import java.util.concurrent.atomic.AtomicLong

import com.waz.api.SyncState
import com.waz.api.impl.ErrorResponse
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model.SyncId
import com.waz.sync.queue.SyncJobMerger.{MergeResult, Merged, Unchanged, Updated}
import com.waz.utils.wrappers.DBCursor
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder}
import org.json.JSONObject

import scala.collection.breakOut
import scala.util.Try

case class SyncJob(override val id:        SyncId,
                                request:   SyncRequest,
                                dependsOn: Set[SyncId]           = Set(),
                                priority:  Int                   = SyncJob.Priority.Normal,
                                timestamp: Long                  = SyncJob.timestamp,
                                startTime: Long                  = 0, // next scheduled execution time
                                attempts:  Int                   = 0,
                                offline:   Boolean               = false,
                                state:     SyncState             = SyncState.WAITING,
                                error:     Option[ErrorResponse] = None)
  extends Identifiable[SyncId] {

  def mergeKey = request.mergeKey

  // `job` is the newer one
  def merge(job: SyncJob): MergeResult[SyncJob] = {
    if (mergeKey != job.mergeKey) Unchanged
    else if (state == SyncState.SYNCING) {
      // ongoing job can only be merged if is duplicate
      if (job.request.isDuplicateOf(request)) Merged(merged(request, job, forceRetry = false)) else Unchanged
    } else {
      request.merge(job.request) match {
        case Unchanged => Unchanged
        case Updated(req) => Updated(job.copy(request = req))
        case Merged(req) => Merged(merged(req, job, forceRetry = false))
      }
    }
  }

  private def merged(req: SyncRequest, job: SyncJob, forceRetry: Boolean): SyncJob =
    copy(request = req,
      priority = priority min job.priority,
      attempts = if (forceRetry) 0 else attempts,
      startTime = if (forceRetry || state != SyncState.FAILED) math.min(startTime, job.startTime) else math.max(startTime, job.startTime),
      dependsOn = dependsOn ++ job.dependsOn)
}


object SyncJob {

  private val lastTimestamp = new AtomicLong(0)

  implicit lazy val Encoder: JsonEncoder[SyncJob] = new JsonEncoder[SyncJob] {
    override def apply(job: SyncJob): JSONObject = JsonEncoder { o =>
      o.put("id", job.id.str)
      o.put("request", JsonEncoder.encode(job.request))
      if (job.dependsOn.nonEmpty) o.put("dependsOn", JsonEncoder.arrString(job.dependsOn.map(_.str).toSeq))
      o.put("priority", job.priority)
      o.put("timestamp", job.timestamp)
      o.put("startTime", job.startTime)
      if (job.attempts != 0) o.put("attempts", job.attempts)
      if (job.offline) o.put("offline", true)
      o.put("state", job.state.name())
      job.error.foreach(e => o.put("error", JsonEncoder.encode(e)))
    }
  }

  implicit lazy val Decoder: JsonDecoder[SyncJob] = new JsonDecoder[SyncJob] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): SyncJob = {
      val state = Try(SyncState.valueOf('state)).toOption.getOrElse(SyncState.WAITING)
      SyncJob(SyncId('id), JsonDecoder[SyncRequest]('request), decodeStringSeq('dependsOn).map(SyncId(_))(breakOut), 'priority, 'timestamp, 'startTime, 'attempts, 'offline, state, opt('error, ErrorResponse.Decoder.apply(_)))
    }
  }

  /**
   * Returns unique timestamp.
   * Uses current time millis or last used time + 1 (whichever is greater).
   */
  def timestamp = {
    val current = System.currentTimeMillis()
    var next = 0L
    while (next < current) {
      next = lastTimestamp.incrementAndGet()
      if (next < current) {
        if (lastTimestamp.compareAndSet(next, current))
          next = current
      }
    }
    next
  }

  object Priority {
    val Critical    = 0
    val High        = 1
    val Normal      = 10
    val Low         = 50
    val Optional    = 100
    val MinPriority = Int.MaxValue
  }

  implicit object SyncJobDao extends Dao[SyncJob, SyncId] {
    val Id = id[SyncId]('_id, "PRIMARY KEY").apply(_.id)
    val Data = json[SyncJob]('data).apply(identity)

    override val idCol = Id
    override val table = Table("SyncJobs", Id, Data)

    override def apply(implicit cursor: DBCursor) = Data
  }
}
