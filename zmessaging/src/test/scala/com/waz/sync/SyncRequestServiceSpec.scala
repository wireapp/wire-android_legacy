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

import java.io.PrintWriter

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.LogTag
import com.waz.api.NetworkMode
import com.waz.api.NetworkMode.UNKNOWN
import com.waz.content.{Database, UserPreferences}
import com.waz.model._
import com.waz.model.sync.{SerialExecutionWithinConversation, SyncJob, SyncRequest}
import com.waz.service._
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.queue.{ConvLock, SyncContentUpdaterImpl}
import com.waz.testutils.TestUserPreferences
import com.waz.threading.CancellableFuture
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.{Context, DB}

import scala.concurrent.Future

class SyncRequestServiceSpec extends AndroidFreeSpec {

  import com.waz.threading.Threading.Implicits.Background

  val context   = mock[Context]
  val db        = mock[Database]
  val network   = mock[NetworkModeService]
  val sync      = mock[SyncHandler]
  val reporting = mock[ReportingService]
  val prefs     = new TestUserPreferences {
    result(this.preference(UserPreferences.ShouldSyncInitial) := false)
    result(this.preference(UserPreferences.ShouldSyncConversations) := false)
  }

  val timeouts = new Timeouts

  val networkMode = Signal[NetworkMode]().disableAutowiring()

  override protected def afterEach() = {
    super.afterEach()
    networkMode ! UNKNOWN
  }

  scenario("Execute a few basic tasks") {
    (sync.apply (_:SyncRequest)).expects(*).returning(Future.successful(SyncResult(true)))
    (sync.apply (_:SerialExecutionWithinConversation, _:ConvLock)).expects(*, *).returning(Future.successful(SyncResult(true)))

    val (handle, service) = getSyncServiceHandle

    result(for {
      id   <- handle.syncSelfUser()
      id2  <- handle.postMessage(MessageId(), ConvId(), RemoteInstant(clock.instant()))
      res  <- service.scheduler.await(id)
      res2 <- service.scheduler.await(id2)
    } yield (res, res2))

    result(service.listJobs.head) should have size 0
  }

  def getSyncServiceHandle = {

    (db.apply[Vector[SyncJob]](_: (DB) => Vector[SyncJob])(_: LogTag)).expects(*, *).anyNumberOfTimes().returning(CancellableFuture.successful(Vector.empty))
    (network.networkMode _).expects().anyNumberOfTimes().returning(networkMode)
    (network.isOnlineMode _).expects().anyNumberOfTimes().returning(false)
    (reporting.addStateReporter(_: (PrintWriter) => Future[Unit])(_: LogTag)).expects(*, *)

    val content = new SyncContentUpdaterImpl(db)
    val service = new SyncRequestServiceImpl(context, account1Id, content, network, sync, reporting, accounts, tracking)
    (new AndroidSyncServiceHandle(service, timeouts, prefs), service)
  }
}
