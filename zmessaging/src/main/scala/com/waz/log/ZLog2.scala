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
package com.waz.log

import com.waz.ZLog.LogTag
import com.waz.log.InternalLog.LogLevel.{Debug, Error, Info, Verbose, Warn}

import scala.annotation.tailrec

object ZLog2 {

  trait LogShow[T] {
    def showSafe(value: T): String
    def showUnsafe(value: T): String = showSafe(value)
  }

  object LogShow {
    def apply[T: LogShow]: LogShow[T] = implicitly[LogShow[T]]
    def create[T](safe: T => String): LogShow[T] = new LogShow[T] {
      override def showSafe(value: T): String = safe(value)
    }

    implicit val ByteLogShow: LogShow[Byte] = create(_.toString)
    implicit val ShortLogShow: LogShow[Short] = create(_.toString)
    implicit val IntLogShow: LogShow[Int] = create(_.toString)
    implicit val LongLogShow: LogShow[Long] = create(_.toString)

    implicit val ThrowableShow: LogShow[Throwable] = create(_.toString)
  }

  trait CanBeShown {
    def showSafe: String
    def showUnsafe: String
  }
  class CanBeShownImpl[T](value: T)(implicit logShow: LogShow[T]) extends CanBeShown {
    override def showSafe: String   = logShow.showSafe(value)
    override def showUnsafe: String = logShow.showUnsafe(value)
  }

  import scala.language.implicitConversions
  implicit def asLogShowArg[T: LogShow](value: T): CanBeShownImpl[T] = new CanBeShownImpl[T](value)

  class Log(stringParts: Iterable[String], args: Iterable[CanBeShown]) {
    def buildMessageSafe: String   = intersperse(stringParts.iterator, args.iterator.map(_.showSafe))
    def buildMessageUnsafe: String = intersperse(stringParts.iterator, args.iterator.map(_.showUnsafe))

    @tailrec
    private def intersperse(xs: Iterator[String], ys: Iterator[String], acc: StringBuilder = new StringBuilder): String = {
      if (xs.hasNext) { acc.append(xs.next()); intersperse(ys, xs, acc) }
      else acc.toString()
    }
  }

  implicit class LogHelper(val sc: StringContext) extends AnyVal {
    def l(args: CanBeShown*): Log = new Log(sc.parts.toList, args)
  }

  def error(log: Log, cause: Throwable)(implicit tag: LogTag): Unit = InternalLog.log(log, cause, Error, tag)
  def error(log: Log)(implicit tag: LogTag): Unit                   = InternalLog.log(log, Error, tag)
  def warn(log: Log, cause: Throwable)(implicit tag: LogTag): Unit  = InternalLog.log(log, cause, Warn, tag)
  def warn(log: Log)(implicit tag: LogTag): Unit                    = InternalLog.log(log, Warn, tag)
  def info(log: Log)(implicit tag: LogTag): Unit                    = InternalLog.log(log, Info, tag)
  def debug(log: Log)(implicit tag: LogTag): Unit                   = InternalLog.log(log, Debug, tag)
  def verbose(log: Log)(implicit tag: LogTag): Unit                 = InternalLog.log(log, Verbose, tag)

}
