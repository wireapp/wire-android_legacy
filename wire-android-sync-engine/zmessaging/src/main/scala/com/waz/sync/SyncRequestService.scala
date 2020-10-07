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

import com.waz.api.SyncState
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.sync._
import com.waz.model.{SyncId, UserId}
import com.waz.service.tracking.TrackingService
import com.waz.service.{AccountContext, AccountsService, NetworkModeService, ReportingService}
import com.waz.sync.queue.{SyncContentUpdater, SyncScheduler, SyncSchedulerImpl}
import com.wire.signals.Signal

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import com.waz.log.LogSE._

trait SyncRequestService {

  def addRequest(account:    UserId,
                 req:        SyncRequest,
                 priority:   Int            = Priority.Normal,
                 dependsOn:  Seq[SyncId]    = Nil,
                 forceRetry: Boolean        = false,
                 delay:      FiniteDuration = Duration.Zero): Future[SyncId]

  def await(ids: Set[SyncId]): Future[Set[SyncResult]]
  def await(id: SyncId): Future[SyncResult]

  def syncState(account: UserId, matchers: Seq[SyncCommand]): Signal[SyncState]
}

class SyncRequestServiceImpl(accountId: UserId,
                             content:   SyncContentUpdater,
                             network:   NetworkModeService,
                             sync: =>   SyncHandler,
                             reporting: ReportingService,
                             accounts:  AccountsService,
                             tracking:  TrackingService
                            )(implicit accountContext: AccountContext) extends SyncRequestService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background
  private val scheduler: SyncScheduler = new SyncSchedulerImpl(accountId, content, network, this, sync, accounts, tracking)

  reporting.addStateReporter { pw =>
    content.listSyncJobs flatMap { jobs =>
      pw.println(s"SyncJobs for account $accountId:")
      jobs.toSeq.sortBy(_.timestamp) foreach { job =>
        pw.println(job.toString)
      }

      pw.println("---")
      scheduler.report(pw)
    }
  }

  override def addRequest(account:    UserId,
                          req:        SyncRequest,
                          priority:   Int            = Priority.Normal,
                          dependsOn:  Seq[SyncId]    = Nil,
                          forceRetry: Boolean        = false,
                          delay:      FiniteDuration = Duration.Zero) = {
    val timestamp = SyncJob.timestamp
    content.addSyncJob(
      SyncJob(SyncId(), req, dependsOn.toSet, priority = priority, timestamp = timestamp, startTime = SyncJob.timestamp + delay.toMillis),
      forceRetry
    ).map(_.id)
  }

  override def await(ids: Set[SyncId]): Future[Set[SyncResult]] =
    scheduler.await(ids)

  override def await(id: SyncId): Future[SyncResult] =
    scheduler.await(id)

  override def syncState(account: UserId, commands: Seq[SyncCommand]) = {
    implicit val logTag: LogTag = LogTag("WorkManager#syncState")
    for {
      jobs     <- content.syncJobs
      filtered =  jobs.filter { case (_, job) => commands.contains(job.request.cmd) }
      _        =  verbose(l"jobs still not completed: ${filtered.toList.mkString(",")}")
      state    =  if (filtered.isEmpty) SyncState.COMPLETED else filtered.minBy(_._2.state.ordinal())._2.state
      _        =  verbose(l"commands: $commands => state: $state")
    } yield state
  }

  //only used in tests currently
  def listJobs =
    content.syncJobs.map(_.values.toSeq.sortBy(j => (j.timestamp, j.priority)))
}
