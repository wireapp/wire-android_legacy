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

import com.waz.log.InternalLog.LogLevel.{Debug, Error, Info, Verbose, Warn}
import com.waz.log.LogShow.{RedactedString, ShowString, Size}

import scala.language.implicitConversions
import scala.annotation.tailrec
import scala.reflect.ClassTag

trait BasicLogging {
  import BasicLogging._

  implicit def toCanBeShown[T: LogShow](value: T): CanBeShownImpl[T] = new CanBeShownImpl[T](value)
  implicit def toLogHelper(sc: StringContext): LogHelper = new LogHelper(sc)

  def error(log: Log, cause: Throwable)(implicit tag: LogTag): Unit = InternalLog.log(log, cause, Error, tag)
  def error(log: Log)(implicit tag: LogTag): Unit                   = InternalLog.log(log, Error, tag)
  def warn(log: Log, cause: Throwable)(implicit tag: LogTag): Unit  = InternalLog.log(log, cause, Warn, tag)
  def warn(log: Log)(implicit tag: LogTag): Unit                    = InternalLog.log(log, Warn, tag)
  def info(log: Log)(implicit tag: LogTag): Unit                    = InternalLog.log(log, Info, tag)
  def debug(log: Log)(implicit tag: LogTag): Unit                   = InternalLog.log(log, Debug, tag)
  def verbose(log: Log)(implicit tag: LogTag): Unit                 = InternalLog.log(log, Verbose, tag)

  def showString(str: String): ShowString = new ShowString(str)
  def redactedString(str: String): RedactedString = new RedactedString(str)
  def asSize(value: Long): Size = new Size(value)

  def logTime[A](log: Log)(body: => A)(implicit tag: LogTag): A = {
    val time = System.nanoTime
    try {
      body
    } finally {
      verbose(l"$log : ${(System.nanoTime - time) / 1000 / 1000f} ms")
    }
  }

}

object BasicLogging {

  class LogTag(val value: String) extends AnyVal
  object LogTag {
    trait DerivedLogTag {
      implicit val logTag: LogTag = LogTag(getClass.getSimpleName)
    }

    implicit val LogTagLogShow: LogShow[LogTag] = LogShow.create(_.value)

    def apply(value: String): LogTag = new LogTag(value)
    def apply[T](implicit ct: ClassTag[T]): LogTag = new LogTag(ct.runtimeClass.getSimpleName)
  }

  def unsafeLog[T](value: T): CanBeShown = new CanBeShownImpl[T](value)

  trait CanBeShown {
    def showSafe: String
    def showUnsafe: String
  }

  class CanBeShownImpl[T](value: T)(implicit logShow: LogShow[T]) extends CanBeShown {
    override def showSafe: String   = logShow.showSafe(value)
    override def showUnsafe: String = logShow.showUnsafe(value)
  }

  class Log(stringParts: Iterable[String], args: Iterable[CanBeShown], private val strip: Boolean = false) {

    def buildMessageSafe: String   = applyModifiers(intersperse(stringParts.iterator, args.iterator.map(_.showSafe)))
    def buildMessageUnsafe: String = applyModifiers(intersperse(stringParts.iterator, args.iterator.map(_.showUnsafe)))

    private def applyModifiers(str: String): String = if (strip) str.stripMargin else str

    def stripMargin: Log = new Log(stringParts, args, strip = true)

    @tailrec
    private def intersperse(xs: Iterator[String], ys: Iterator[String], acc: StringBuilder = new StringBuilder): String = {
      if (xs.hasNext) { acc.append(xs.next()); intersperse(ys, xs, acc) }
      else acc.toString()
    }
  }

  object Log {
    implicit val LogLogShow: LogShow[Log] = LogShow.create(_.buildMessageSafe, _.buildMessageUnsafe)
  }

  class LogHelper(val sc: StringContext) extends AnyVal {
    def l(args: CanBeShown*): Log = new Log(sc.parts.toList, args)
  }

}
