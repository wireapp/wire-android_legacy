/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.background

import java.util.UUID

import android.app.Application
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.runner.{AndroidJUnit4, AndroidJUnitRunner}
import androidx.work.test.WorkManagerTestInitHelper
import com.waz.ZLog.ImplicitTag._
import com.waz.api.{NetworkMode, SyncState}
import com.waz.log.{AndroidLogOutput, InternalLog}
import com.waz.model.sync.SyncRequest
import com.waz.model.sync.SyncRequest.SyncSelf
import com.waz.model.{SyncId, UserId}
import com.waz.service.NetworkModeService
import com.waz.service.tracking.TrackingService
import com.waz.sync.{SyncHandler, SyncResult}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injector, Module, WireContext}
import org.junit.runner.RunWith
import org.junit.{Before, Test}
import org.mockito.Matchers._
import org.mockito.Mockito
import org.threeten.bp.Clock

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

@RunWith(classOf[AndroidJUnit4])
class WorkManagerSyncRequestServiceSpec {

  implicit val ec: ExecutionContext = Threading.Background

  val account = UserId("account")
  val network = Signal(NetworkMode.WIFI)

  implicit var context: Context = _
  implicit var app: TestApplication = _
  implicit def injector: Injector = app.injector
  implicit def eventContext: EventContext = app.eventContext

  private var networkService  = Mockito.mock(classOf[NetworkModeService], "networkModeService")
  private var syncHandler     = Mockito.mock(classOf[SyncHandler], "syncHandler")
  private var tracking        = Mockito.mock(classOf[TrackingService], "trackingService")

  @Before
  def beforeAll(): Unit = {
    context  = InstrumentationRegistry.getTargetContext
    app      = context.getApplicationContext.asInstanceOf[TestApplication]

    app.testModule = new Module {
      bind[WorkManagerSyncRequestService] to new WorkManagerSyncRequestService()(injector, context, eventContext)
      bind[Clock]                         to Clock.systemUTC()
      bind[NetworkModeService]            to networkService
      bind[SyncHandler]                   to syncHandler
      bind[TrackingService]               to tracking
    }

    WorkManagerTestInitHelper.initializeTestWorkManager(context)
    Mockito.when(networkService.networkMode).thenReturn(network)
  }

  @Test
  def liveDataSignal(): Unit = {
    val liveData = new MutableLiveData[Int]()
    val signal = new LiveDataSignal(liveData)
    assert(signal.currentValue.isEmpty)
    liveData.postValue(1)
    assert(Await.result(signal.collect { case s if s == 1 => true }.head, 3.seconds))
  }

  @Test
  def awaitRecentlyScheduledSyncJob(): Unit = {

    val service = injector[WorkManagerSyncRequestService]()
    val testDriver = WorkManagerTestInitHelper.getTestDriver

    val jobFinishedPromise = Promise[SyncResult]()
    Mockito.when(syncHandler.apply(any(), any())(any())).thenReturn(jobFinishedPromise.future)

    val id  = Await.result(service.addRequest(account, SyncSelf), 3.seconds)
    val res = service.await(id)

    setConstraintsMet(id)
    jobFinishedPromise.trySuccess(SyncResult.Success)

    assert(Await.result(res, 3.seconds) == SyncResult.Success)
  }

  @Test
  def testObservingSyncState(): Unit = {

    val service = injector[WorkManagerSyncRequestService]()

    val jobFinishedPromise = Promise[SyncResult]()
    Mockito.when(syncHandler.apply(any(), any())(any())).thenReturn(jobFinishedPromise.future)

    val id  = service.addRequest(account, SyncRequest.SyncSelf)
    val state = service.syncState(account, Seq(SyncSelf.cmd))

    val passed = for {
      id <- id
      _ <- state.filter(_ == SyncState.WAITING).head
      _ = setConstraintsMet(id)
      _ <- state.filter(_ == SyncState.SYNCING).head
      _ = jobFinishedPromise.trySuccess(SyncResult.Success)
      _ <- state.filter(_ == SyncState.COMPLETED).head
    } yield true

    assert(Await.result(passed, 5.seconds))
  }

  /**
    * setAllConstraintsMet on the test driver synchronously blocks until the Worker#doWork implementation completes.
    * This is just a wrapper to post the method on the background thread, so that the test can continue and we can
    * complete the work later.
    */
  def setConstraintsMet(id: SyncId): Unit = {
    Future(WorkManagerTestInitHelper.getTestDriver.setAllConstraintsMet(UUID.fromString(id.str)))(Threading.Background)
  }
}

class TestApplication extends Application with WireContext {
  override def eventContext: EventContext = EventContext.Global

  var testModule: Module = _

  override implicit def injector: Injector = testModule

  override def onCreate(): Unit = {
    super.onCreate()
    InternalLog.add(new AndroidLogOutput)
  }
}


class TestRunner extends AndroidJUnitRunner {
  override def newApplication(cl: ClassLoader, className: String, context: Context): Application =
    super.newApplication(cl, classOf[TestApplication].getName, context)
}
