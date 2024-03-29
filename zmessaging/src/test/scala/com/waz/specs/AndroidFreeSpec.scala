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
package com.waz.specs

import java.util.concurrent.{Executors, ThreadFactory, TimeoutException}
import com.waz.SystemLogOutput
import com.waz.log.{InternalLog, LogsService}
import com.waz.model.{QualifiedId, UserId}
import com.waz.model.otr.ClientId
import com.waz.service.AccountsService.{AccountState, InForeground, LoggedOut}
import com.waz.service._
import com.waz.service.assets.FileRestrictionList
import com.waz.service.tracking.TrackingService
import com.waz.testutils.TestClock
import com.waz.threading.Threading.{Background, IO, ImageDispatcher, Ui}
import com.wire.signals.{CancellableFuture, DispatchQueue, Signal}
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.wrappers.{Intent, JVMIntentUtil, JavaURIUtil, URI, _}
import org.junit.runner.RunWith
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

object FutureAwaitSyntax {
  val DefaultTimeout: FiniteDuration = 30.seconds
}

trait FutureAwaitSyntax {
  import FutureAwaitSyntax._

  def await(future: Future[_])(implicit duration: FiniteDuration = DefaultTimeout): Unit = {
    Await.result(future, duration)
  }

  def result[A](future: Future[A])(implicit duration: FiniteDuration = DefaultTimeout): A =
    Await.result(future, duration)
}

trait ZSpec extends FeatureSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with Matchers
  with OneInstancePerTest
  with FutureAwaitSyntax {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    isTest = true

    InternalLog.reset()
    InternalLog.add(new SystemLogOutput)
    InternalLog.setLogsService(new DummyLogsService)
  }
}

trait ZMockSpec extends ZSpec with MockFactory

@RunWith(classOf[JUnitRunner])
abstract class AndroidFreeSpec extends ZMockSpec { this: Suite =>

  import AndroidFreeSpec._

  val clock = AndroidFreeSpec.clock

  val account1Id  = UserId("account1")
  val qualified1Id  = QualifiedId(account1Id)
  val client1Id   = ClientId("client1")
  val accounts    = mock[AccountsService]
  val tracking    = mock[TrackingService]

  ZMessaging.currentGlobal = new EmptyGlobalModule {
    override def trackingService = tracking

    override def fileRestrictionList: FileRestrictionList = new FileRestrictionList("", false)
  }
  ZMessaging.globalReady = Promise[GlobalModule]()
  ZMessaging.globalReady.success(ZMessaging.currentGlobal)

  val accountStates = Signal[Map[UserId, AccountState]](Map(account1Id -> InForeground))

  (accounts.accountState _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
    accountStates.map(_.getOrElse(id, LoggedOut))
  }

  def updateAccountState(id: UserId, state: AccountState) =
    accountStates.mutate(_ + (id -> state))

  lazy implicit val accountContext = new AccountContext(account1Id, accounts)

  override protected def beforeEach() = {
    clock.reset()
  }

  //Ensures that Android wrappers are assigned with a non-Android implementation so that tests can run on the JVM
  override protected def beforeAll() = {
    super.beforeAll()

    URI.setUtil(JavaURIUtil)

    DB.setUtil(new DBUtil {
      override def ContentValues(): DBContentValues = DBContentValuesMap()
    })

    ZMessaging.clock = clock

    Intent.setUtil(JVMIntentUtil)

    Threading.setUi(DispatchQueue(
      DispatchQueue.Serial,
      Executors.newSingleThreadExecutor(new ThreadFactory {
        override def newThread(r: Runnable) = new Thread(r, Threading.testUiThreadName)
      }),
      Threading.testUiThreadName
    ))
  }

  /**
    * Here we wait for all threads to finish their current tasks as to allow each test to run with a clean threading profile.
    * If there are still tasks pending, we fail, as it likely means an error somewhere.
    */
  override def withFixture(test: NoArgTest) = {
    val res = super.withFixture(test)
    res match {
      case Succeeded =>
        if (!tasksCompletedAfterWait)
          Failed(new TimeoutException(s"Background tasks continued running after test for ${DefaultTimeout.toSeconds} seconds: Potential threading issue!"))
        else if (swallowedFailure.isDefined) {
          returning(Failed(swallowedFailure.get)) { _ =>
            swallowedFailure = None
          }
        }
        else
          Succeeded
      case outcome => outcome
    }
  }

  /**
    * Very useful for checking that something DOESN'T happen (e.g., ensure that a signal doesn't get updated after
    * performing a series of actions)
    */
  def awaitAllTasks(implicit timeout: FiniteDuration = DefaultTimeout) = {
    if (!tasksCompletedAfterWait) fail(new TimeoutException(s"Background tasks didn't complete in ${timeout.toSeconds} seconds"))
  }

  def tasksRemaining = Seq(IO, ImageDispatcher, Ui, Background).exists(_.hasRemainingTasks)

  private def tasksCompletedAfterWait(implicit timeout: FiniteDuration = DefaultTimeout) = {
    val start = Instant.now
    while(tasksRemaining && Instant.now().isBefore(start + timeout)) Thread.sleep(10)
    !tasksRemaining
  }

  def withDelay[T](body: => T, delay: FiniteDuration = 300.millis)(implicit ec: ExecutionContext) = CancellableFuture.delayed(delay)(body)
}

object AndroidFreeSpec {
  val clock = TestClock()

  val DefaultTimeout = 30.seconds
  @volatile private var swallowedFailure = Option.empty[exceptions.TestFailedException]
}

class DummyLogsService extends LogsService {
  override def logsEnabledGlobally: Signal[Boolean] = Signal.const(true)
  override def logsEnabled: Future[Boolean] = ???
  override def setLogsEnabled(enabled: Boolean): Future[Unit] = ???
}
