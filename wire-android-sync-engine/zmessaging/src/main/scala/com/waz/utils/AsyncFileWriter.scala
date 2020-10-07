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

import java.io.{File, FileOutputStream}
import java.nio.ByteBuffer

import com.wire.signals.SerialDispatchQueue
import com.waz.threading.Threading.{Background,IO}

import scala.concurrent.Future.successful
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

class AsyncFileWriter(file: File) {
  private val serialDispatcher = SerialDispatchQueue()
  private val promisedCompletion = Promise[Unit]
  @volatile private var finishCalled = false
  @volatile private var activeStream = Option.empty[FileOutputStream]
  @volatile private var queue = successful(())

  def completion: Future[Unit] = promisedCompletion.future

  def enqueue(b: ByteBuffer): Unit = completion.value match {
    case Some(Failure(cause)) =>
      throw cause
    case Some(Success(())) =>
      throw new IllegalStateException("already completed")
    case None =>
      if (finishCalled) throw new IllegalStateException("enqueue(…) after finish()")
      serialDispatcher {
        queue = queue.flatMap { _ =>
          Future[Unit] {
            try {
              activeStream.getOrElse(returning(new FileOutputStream(file))(s => activeStream = Some(s))).getChannel.write(b)
            } catch {
              case t: Throwable =>
                Try(activeStream.foreach(_.close()))
                promisedCompletion.tryFailure(t)
                throw t
            }
          }(IO)
        }(Background)
      }
  }

  def finish(): Unit = {
    finishCalled = true
    serialDispatcher(queue = queue.map { _ => closeUnsafe() }(Background))
  }

  override protected def finalize: Unit = closeUnsafe()

  private def closeUnsafe(): Unit =
    try {
      activeStream.foreach(_.close())
      promisedCompletion.trySuccess(())
    } catch {
      case t: Throwable => promisedCompletion.tryFailure(t)
    }
}
