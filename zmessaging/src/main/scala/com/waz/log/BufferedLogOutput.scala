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

import java.io.{BufferedWriter, File, FileWriter, IOException}

import com.waz.log.BasicLogging.LogTag
import com.waz.log.InternalLog.{LogLevel, dateTag, stackTrace}
import com.wire.signals.{DispatchQueue, SerialDispatchQueue}
import com.waz.threading.Threading
import com.waz.utils.crypto.ZSecureRandom
import com.waz.utils.returning

import scala.annotation.tailrec
import scala.collection.JavaConversions._

class BufferedLogOutput(baseDir: String,
                        override val showSafeOnly: Boolean = false,
                        maxBufferSize: Long = BufferedLogOutput.DefMaxBufferSize,
                        maxFileSize: Long = BufferedLogOutput.DefMaxFileSize,
                        maxRollFiles: Int = BufferedLogOutput.DefMaxRollFiles) extends LogOutput {

  assert(maxBufferSize < maxFileSize)
  assert(maxRollFiles > 0)

  override val id: String = "BufferedLogOutput" + ZSecureRandom.nextInt().toHexString

  private implicit val dispatcher = DispatchQueue(DispatchQueue.SERIAL, Threading.IO, Some(id))

  private val buffer = StringBuilder.newBuilder
  private val pathRegex = s"$baseDir/${BufferedLogOutput.DefFileName}([0-9]+).log".r
  private var paths = this.synchronized {
    asScalaIterator(new File(baseDir).listFiles().iterator)
      .map(_.getAbsolutePath)
      .collect { case path @ pathRegex(index) => (path, -index.toInt) }
      .toList
      .sortBy(_._2)
      .map(_._1)
  }

  private def newPath = s"$baseDir/${BufferedLogOutput.DefFileName}${paths.size}.log"

  def currentPath: String = {
    if (paths.isEmpty) paths = List(newPath)
    while (new File(paths.head).length > maxFileSize) paths = newPath :: paths
    paths.head
  }

  //for tests
  def getMaxBufferSize: Long = maxBufferSize

  // internally the first path is the youngest one, but to the outside we want to show paths from the oldest to the youngest
  def getPaths: List[String] = paths.reverse

  override def log(str: String, level: LogLevel, tag: LogTag, ex: Option[Throwable] = None): Unit = this.synchronized {
    buffer.append(s"$dateTag/$level/${tag.value}: $str\n${ex.map(e => s"${stackTrace(e)}\n").getOrElse("")}")
    if (size > maxBufferSize) flush()
  }

  override def close(): Unit = flush()

  def empty: Boolean = buffer.isEmpty

  def size: Int = buffer.length

  // TODO: In this implementation we risk that writing to the file fails and we lose the contents.
  // But if we wait for the result of writeToFile, we risk that meanwhile someone will add something
  // to the buffer and we will lose that.
  override def flush(): Unit = this.synchronized {
    if (!empty) {
      val path = currentPath
      val contents = buffer.toString
      buffer.clear()
      writeToFile(path, contents)
      while (paths.size > maxRollFiles)
        paths = BufferedLogOutput.roll(paths.reverse)
    }
  }

  override def clear(): Unit = {
    flush()
    paths foreach { path => new File(path).delete() }
  }

  private def writeToFile(fileName: String, contents: String): Unit = this.synchronized {
    try {
      val file = new File(fileName)
      if (!file.exists) {
        file.getParentFile.mkdirs()
        file.createNewFile()
        file.setReadable(true)
        file.setWritable(true)
      }

      returning(new BufferedWriter(new FileWriter(file, true))) { writer =>
        writer.write(contents)
        writer.flush()
        writer.close()
      }
    } catch {
      case ex: IOException => ex.printStackTrace()
    }
  }

  // delete the old "internalLog.log" file, from before rolling was introduced - this code can be deleted after some time
  private def deleteOldInternalLog() = {
    val oldLog = new File(s"$baseDir/internalLog.log")
    if(oldLog.exists()) oldLog.delete()
  }
  deleteOldInternalLog()
}

object BufferedLogOutput {

  val DefMaxBufferSize = 256L * 1024L
  val DefMaxFileSize = 4L * DefMaxBufferSize
  val DefMaxRollFiles = 10
  val DefFileName = "internalLog"

  @tailrec
  private def roll(pathsSorted: List[String], newPaths: List[String] = Nil): List[String] = pathsSorted match {
    case first :: second :: tail =>
      new File(second).renameTo(new File(first))
      roll(second :: tail, first :: newPaths)
    case _ => newPaths
  }

}
