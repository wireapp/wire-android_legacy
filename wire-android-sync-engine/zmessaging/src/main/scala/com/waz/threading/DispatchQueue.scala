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
package com.waz.threading

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import android.os.{Handler, Looper}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.utils.crypto.ZSecureRandom

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

trait DispatchQueue extends ExecutionContext {

  private[threading] val name: String = "queue_" + ZSecureRandom.nextInt().toHexString

  /**
   * Executes a task on this queue.
   * @param task - operation to perform on this queue.
   */
  def apply[A](task: => A)(implicit tag: LogTag = LogTag("")): CancellableFuture[A] = CancellableFuture(task)(this, tag)

  //TODO: this implements ExecutionContext.reportFailure, should we use different log here? or maybe do something else
  override def reportFailure(t: Throwable): Unit = error(l"reportFailure called", t)(LogTag(name))

  //used for waiting in tests
  def hasRemainingTasks: Boolean = false

}

object DispatchQueue {
  def apply(concurrentTasks: Int = 0, executor: ExecutionContext = Threading.ThreadPool) = concurrentTasks match {
    case 0 => new UnlimitedDispatchQueue(executor)
    case 1 => new SerialDispatchQueue(executor)
    case _ => new LimitedDispatchQueue(concurrentTasks, executor)
  }
}

class UnlimitedDispatchQueue(executor: ExecutionContext = Threading.ThreadPool, override val name: String = "UnlimitedQueue") extends DispatchQueue {
  override def execute(runnable: Runnable): Unit = executor.execute(DispatchQueueStats(name, runnable))
}

class UiDispatchQueue() extends DispatchQueue {
  override private[threading] val name: String = "UiDispatchQueue"
  private val handler = new Handler(Looper.getMainLooper)

  override def execute(runnable: Runnable): Unit = handler.post(DispatchQueueStats(name, runnable))
}

/**
 * Execution context limiting number of concurrently executing tasks.
 * All tasks are executed on parent execution context.
 */
class LimitedDispatchQueue(concurrencyLimit: Int = 1, parent: ExecutionContext = Threading.ThreadPool, override val name: String = "LimitedQueue") extends DispatchQueue {
  require(concurrencyLimit > 0, "concurrencyLimit should be greater than 0")

  override def execute(runnable: Runnable): Unit = Executor.dispatch(runnable)

  override def reportFailure(cause: Throwable): Unit = parent.reportFailure(cause)

  private object Executor extends Runnable {

    val queue = new ConcurrentLinkedQueue[Runnable]
    val runningCount = new AtomicInteger(0)

    def dispatch(runnable: Runnable): Unit = {
      queue.add(DispatchQueueStats(name, runnable))
      dispatchExecutor()
    }

    def dispatchExecutor(): Unit = {
      if (runningCount.getAndIncrement < concurrencyLimit)
        parent.execute(this)
      else if (runningCount.decrementAndGet() < concurrencyLimit && !queue.isEmpty)
        dispatchExecutor() // to prevent race condition when executor has just finished
    }

    override def run(): Unit = {

      @tailrec
      def executeBatch(counter: Int = 0): Unit = queue.poll() match {
        case null => // done
        case runnable =>
          try {
            runnable.run()
          } catch {
            case cause: Throwable => reportFailure(cause)
          }
          if (counter < LimitedDispatchQueue.MaxBatchSize) executeBatch(counter + 1)
      }

      executeBatch()

      if (runningCount.decrementAndGet() < concurrencyLimit && !queue.isEmpty)
        dispatchExecutor()
    }
  }

  override def hasRemainingTasks = !Executor.queue.isEmpty || Executor.runningCount.get() > 0
}

object LimitedDispatchQueue {
  /**
   * Maximum number of tasks to execute in single batch.
   * Used to prevent starving of other contexts using common parent.
   */
  val MaxBatchSize = 100
}

class SerialDispatchQueue(executor: ExecutionContext = Threading.ThreadPool, override val name: String = "serial_" + ZSecureRandom.nextInt().toHexString) extends LimitedDispatchQueue(1, executor)

object SerialDispatchQueue {
  def apply()(implicit logTag: LogTag): SerialDispatchQueue = new SerialDispatchQueue(name = s"${logTag}_SerialDispatchQueue")
}