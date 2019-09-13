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
package com.waz.utils

import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AssetData
import com.waz.model.AssetData.ProcessingTaskKey
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Global registry of cancellable tasks.
  */
object Cancellable {
  private implicit val dispatcher: SerialDispatchQueue = new SerialDispatchQueue(name = "Cancellable")

  private val tasks = new mutable.HashMap[Any, CancellableFuture[_]]

  def apply[A](key: Any*)(task: CancellableFuture[A]): CancellableFuture[A] = dispatcher {
    tasks(key) = task
    task.onComplete { _ => if (tasks.get(key).contains(task)) tasks -= key }
    task
  } .flatten

  def cancel(key: Any*)(implicit tag: LogTag) = Future {
    tasks.remove(key).foreach { task =>
      verbose(l"canceling task: $task")
      task.cancel()
    }
  }
}

object AssetProcessing extends DerivedLogTag {
  private implicit val dispatcher = new SerialDispatchQueue(name = "AssetProcessing")

  private val tasks = new mutable.HashMap[ProcessingTaskKey, CancellableFuture[Option[AssetData]]]

  def get(key: ProcessingTaskKey) = {
    verbose(l"getting processing task for key: $key, has value?: ${tasks.contains(key)}")
    tasks.getOrElse(key, CancellableFuture successful None)
  }

  def apply(key: ProcessingTaskKey)(task: CancellableFuture[Option[AssetData]]): CancellableFuture[Option[AssetData]] = dispatcher {
    verbose(l"adding processing entry for key: $key")
    tasks(key) = task
    task.onComplete { _ => if (tasks.get(key).contains(task)) tasks -= key }
    task
  }.flatten

  def cancel(key: ProcessingTaskKey)(implicit tag: LogTag) = Future {
    tasks.remove(key).foreach { task =>
      verbose(l"canceling asset processing task: $task")(tag)
      task.cancel()(tag)
    }
  }
}
