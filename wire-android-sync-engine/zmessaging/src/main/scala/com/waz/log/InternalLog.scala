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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.BasicLogging.{Log, LogTag}
import com.waz.service.ZMessaging.clock
import com.waz.utils.events.Signal

import scala.Ordered._
import scala.collection.mutable

object InternalLog extends DerivedLogTag {

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

  private var logsEnabled: Signal[Boolean] = Signal.empty

  def setLogsService(logsService: LogsService): Unit = this.synchronized {
    logsEnabled = logsService.logsEnabledGlobally
  }

  private val outputs = mutable.HashMap[String, LogOutput]()

  def getOutputs: List[LogOutput] = outputs.values.toList

  def reset(): Unit = this.synchronized {
    outputs.values.foreach(_.close())
    outputs.clear
  }

  def flush(): Unit = outputs.values.foreach(_.flush())

  def apply(id: String): Option[LogOutput] = outputs.get(id)

  def add(output: LogOutput): LogOutput = this.synchronized {
    outputs.getOrElseUpdate(output.id, output)
  }

  def remove(output: LogOutput): Unit = this.synchronized { outputs.remove(output.id) match {
    case Some(o) => o.close()
    case _ =>
  } }

  def stackTrace(cause: Throwable): String = Option(cause) match {
    case Some(c) => val result = new StringWriter()
                    c.printStackTrace(new PrintWriter(result))
                    result.toString

    case None    => ""
  }

  def dateTag = s"${clock.instant().toString}-TID:${Thread.currentThread().getId}"

  def log(log: Log, level: LogLevel, tag: LogTag): Unit =
    writeLog(log, level, out => out.log(_, level, tag))

  def log(log: Log, cause: Throwable, level: LogLevel, tag: LogTag): Unit =
    writeLog(log, level, out => out.log(_, cause, level, tag))

  def clearAll(): Unit = outputs.valuesIterator.foreach(_.clear())

  private def writeLog(log: Log, level: LogLevel, logMsgConsumerCreator: LogOutput => String => Unit): Unit = {
    if (logsEnabled.currentValue.contains(true)) {
      outputs.values.filter(_.level <= level).foreach { output =>
        val logMessage =
          if (output.showSafeOnly) log.buildMessageSafe
          else log.buildMessageUnsafe

        logMsgConsumerCreator(output)(logMessage)
      }
    }
  }

}
