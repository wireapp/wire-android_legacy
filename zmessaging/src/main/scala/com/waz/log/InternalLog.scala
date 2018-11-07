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

import java.io._

import com.waz.ZLog.LogTag
import com.waz.log.ZLog2.Log
import com.waz.service.ZMessaging.clock

import scala.Ordered._
import scala.collection.mutable

//TODO Merge this class with ZLog2 after migration period
object InternalLog {
  sealed trait LogLevel
  object LogLevel {
    case object Error   extends LogLevel { override def toString = "E" }
    case object Warn    extends LogLevel { override def toString = "W" }
    case object Info    extends LogLevel { override def toString = "I" }
    case object Debug   extends LogLevel { override def toString = "D" }
    case object Verbose extends LogLevel { override def toString = "V" }

    def weight(level: LogLevel): Int = level match {
      case Verbose => 1
      case Debug => 2
      case Info => 3
      case Warn => 4
      case Error => 5
    }

    implicit val ordering: Ordering[LogLevel] = Ordering by weight
  }

  private val outputs = mutable.HashMap[String, LogOutput]()

  def getOutputs: List[LogOutput] = outputs.values.toList

  def reset(): Unit = this.synchronized {
    outputs.values.foreach( _.close() )
    outputs.clear
  }

  def flush(): Unit = outputs.values.foreach( _.flush() )

  def apply(id: String): Option[LogOutput] = outputs.get(id)

  def add(output: LogOutput): LogOutput = this.synchronized {
    outputs.getOrElseUpdate(output.id, output)
  }

  def remove(output: LogOutput) = this.synchronized { outputs.remove(output.id) match {
    case Some(o) => o.close()
    case _ =>
  } }

  import LogLevel._
  def error(msg: String, cause: Throwable, tag: LogTag): Unit = log(msg, cause, Error, tag)
  def error(msg: String, tag: LogTag): Unit                   = log(msg, Error, tag)
  def warn(msg: String, cause: Throwable, tag: LogTag): Unit  = log(msg, cause, Warn, tag)
  def warn(msg: String, tag: LogTag): Unit                    = log(msg, Warn, tag)
  def info(msg: String, tag: LogTag): Unit                    = log(msg, Info, tag)
  def debug(msg: String, tag: LogTag): Unit                   = log(msg, Debug, tag)
  def verbose(msg: String, tag: LogTag): Unit                 = log(msg, Verbose, tag)

  def stackTrace(cause: Throwable): LogTag = Option(cause) match {
    case Some(c) => val result = new StringWriter()
                    c.printStackTrace(new PrintWriter(result))
                    result.toString

    case None    => ""
  }

  def dateTag = s"${clock.instant().toString}-TID:${Thread.currentThread().getId}"

  private def log(msg: String, level: LogLevel, tag: LogTag): Unit =
    outputs.values.foreach { _.log(msg, level, tag) }

  private def log(msg: String, cause: Throwable, level: LogLevel, tag: LogTag): Unit =
    outputs.values.foreach { _.log(msg, cause, level, tag) }

  def log(log: Log, level: LogLevel, tag: LogTag): Unit =
    writeLog(log, level, out => out.log(_, level, tag))

  def log(log: Log, cause: Throwable, level: LogLevel, tag: LogTag): Unit =
    writeLog(log, level, out => out.log(_, cause, level, tag))

  private def writeLog(log: Log, level: LogLevel, logMsgConsumerCreator: LogOutput => String => Unit): Unit = {
    outputs.values.filter(_.level <= level).foreach { output =>
      val logMessage =
        if (output.showSafeOnly) log.buildMessageSafe
        else log.buildMessageUnsafe

      logMsgConsumerCreator(output)(logMessage)
    }
  }

}