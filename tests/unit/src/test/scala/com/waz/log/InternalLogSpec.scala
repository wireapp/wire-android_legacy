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

import java.io.{BufferedReader, File, FileInputStream, InputStreamReader}

import com.waz.api.ZmsVersion
import com.waz.specs.AndroidFreeSpec
import com.waz.utils.IoUtils

import scala.util.Random

import scala.collection.JavaConversions._

class InternalLogSpec extends AndroidFreeSpec {
  val tag = "InternalLogSuite"
  val tempDir = System.getProperty("java.io.tmpdir")+"tmp"+System.nanoTime()

  def filesNumberInTempDir = new File(tempDir).listFiles().length
  def pathsInTempDir = asScalaIterator(new File(tempDir).listFiles().iterator).map(_.getAbsolutePath).toList.sorted

  def overflow(log: BufferedLogOutput) = {
    var prevSize = -1L
    while (log.getMaxBufferSize > log.size && log.size > prevSize) {
      prevSize = log.size
      InternalLog.debug(Random.nextPrintableChar().toString, tag)
      Thread.sleep(100L) // simulating much longer logs and much bigger buffers
    }
  }

  def exists(fileName: String) = new File(fileName).exists

  def delete(f: File) : Unit = if (f.exists) {
    if (f.isDirectory) f.listFiles().foreach(delete)
    f.delete()
  }

  def read(path: String) = {
    val file = new File(path)
    val sb = StringBuilder.newBuilder
    IoUtils.withResource(new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
      reader => Iterator.continually(reader.readLine()).takeWhile(_ != null).foreach(line => sb.append(line).append('\n'))
    }
    sb.toString
  }

  override protected def beforeEach() = {
    super.beforeEach()
    InternalLog.reset()

    val file = new File(tempDir)
    delete(file)
    file.mkdir()
  }

  override protected def afterEach() = {
    InternalLog.reset()
    delete(new File(tempDir))
  }

  feature("adding and removing log outputs") {
    scenario("adds and removes a buffered log output") {
      InternalLog.getOutputs.size shouldEqual(0)
      val logId = InternalLog.add(new BufferedLogOutput(tempDir)).id
      InternalLog.getOutputs.size shouldEqual(1)

      val log = InternalLog(logId).getOrElse(fail(s"No log output: $tempDir"))
      log.isInstanceOf[BufferedLogOutput] shouldEqual(true)

      InternalLog.remove(log)
      InternalLog.getOutputs.size shouldEqual(0)
    }

    scenario("adds and removes an Android log output") {
      InternalLog.getOutputs.size shouldEqual(0)
      InternalLog.add(new AndroidLogOutput)
      InternalLog.getOutputs.size shouldEqual(1)

      val log = InternalLog("android").getOrElse(fail(s"No log output for Android"))
      log.isInstanceOf[AndroidLogOutput] shouldEqual(true)

      InternalLog.remove(log)
      InternalLog.getOutputs.size shouldEqual(0)
    }

    scenario("reset log outputs") {
      InternalLog.getOutputs.size shouldEqual(0)
      InternalLog.add(new BufferedLogOutput(tempDir))
      InternalLog.add(new AndroidLogOutput)
      InternalLog.getOutputs.size shouldEqual(2)
      InternalLog.reset()
      InternalLog.getOutputs.size shouldEqual(0)
    }
  }

  feature("writing logs to the buffer") {
    scenario("creates an empty buffer") {
      val log = new BufferedLogOutput(tempDir)
      InternalLog.add(log)
      log.empty shouldEqual(true)
    }

    scenario("appends to the buffer") {
      val log = new BufferedLogOutput(tempDir)
      InternalLog.add(log)
      log.empty should equal(true)
      InternalLog.debug("something", tag)
      log.empty shouldEqual(false)
    }

    scenario("clears the buffer when full") {
      val log = new BufferedLogOutput(tempDir, 128L)
      InternalLog.add(log)
      log.empty shouldEqual(true)

      InternalLog.debug("!", tag)
      log.empty shouldEqual(false)

      overflow(log)

      log.empty shouldEqual(true)
    }
  }

  feature("writing logs to the file") {
    scenario("creates a log file") {
      filesNumberInTempDir shouldEqual(0)

      val log = new BufferedLogOutput(tempDir, 128L)
      InternalLog.add(log)
      filesNumberInTempDir shouldEqual(0)

      overflow(log)

      filesNumberInTempDir shouldEqual(1)
    }

    scenario("appends to the log file when the buffer is full") {
      val log = new BufferedLogOutput(tempDir, 128L)
      InternalLog.add(log)
      overflow(log)
      val fileSize1 = new File(log.currentPath).length()
      overflow(log)
      val fileSize2 = new File(log.currentPath).length()
      fileSize2 > fileSize1 shouldEqual(true)
    }
  }

  feature("connecting with ZLog") {

    scenario("receives logs written to ZLog") {
      if (ZmsVersion.DEBUG) {
        val log = new BufferedLogOutput(tempDir)
        InternalLog.add(log)
        log.empty shouldEqual(true)

        import com.waz.ZLog._
        import com.waz.ZLog.ImplicitTag._

        verbose("something")

        log.empty shouldEqual(false)
      }
    }
  }

  feature("log file rolling") {
    val maxBufferSize = 128L
    val maxFileSize = maxBufferSize * 4L

    scenario("creates a new file when the max file size limit is exceeded") {
      val log = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize)
      InternalLog.add(log)
      exists(log.currentPath) shouldEqual(false)
      filesNumberInTempDir shouldEqual(0)

      overflow(log)
      exists(log.currentPath) shouldEqual(true)
      filesNumberInTempDir shouldEqual(1)

      val firstPath = log.currentPath

      (1 to 4).foreach( _ => overflow(log) )

      exists(log.currentPath) shouldEqual(true)
      filesNumberInTempDir shouldEqual(2)
      log.currentPath should not equal(firstPath)
    }

    scenario("it's ok for the file to be a bit bigger than the limit") {
      val log = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize)
      InternalLog.add(log)

      val firstPath = log.currentPath

      (1 to 5).foreach( _ => overflow(log) )
      filesNumberInTempDir shouldEqual(2)

      val fileLen = new File(firstPath).length()

      (fileLen > maxFileSize) shouldEqual(true)
      (fileLen < 2L * maxFileSize) shouldEqual(true)
    }

    scenario("returns all file paths") {
      val log = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize)
      InternalLog.add(log)
      
      (1 to 5).foreach( _ => overflow(log) )
      if (new File(log.currentPath).exists()) { // the current file may not yet exist (if there was no flush)
        log.getPaths.size shouldEqual (filesNumberInTempDir)
      } else {
        log.getPaths.size shouldEqual (filesNumberInTempDir) + 1
      }

      (1 to 5).foreach( _ => overflow(log) )
      val paths = if (new File(log.currentPath).exists()) log.getPaths else log.getPaths.tail
      paths.size shouldEqual (filesNumberInTempDir)
      val pathsSorted = paths.sortBy(p => new File(p).lastModified())
      paths shouldEqual(pathsSorted)
    }

    scenario("deletes the oldest file and rolls the rest") {
      val maxRollFiles = 4
      val log = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      InternalLog.add(log)

      while (filesNumberInTempDir < maxRollFiles) overflow(log)
      log.getPaths.size shouldEqual (filesNumberInTempDir)

      val oldPaths = log.getPaths

      oldPaths.size shouldEqual(maxRollFiles)
      val oldFiles = oldPaths.map(read)
      for (i <- 0 to oldFiles.size - 2) {
        oldFiles(i) should not equal oldFiles(i + 1)
      }

      (1 to 6).foreach( _ => overflow(log) )
      log.getPaths.size shouldEqual (filesNumberInTempDir)

      val newPaths = log.getPaths
      val newFiles = newPaths.map(read)
      for (i <- 0 to newFiles.size - 2) {
        newFiles(i) should not equal newFiles(i + 1)
      }

      newPaths.size shouldEqual(maxRollFiles)
      oldPaths shouldEqual(newPaths)

      for (i <- 0 to newFiles.size - 3) {
        oldFiles(i+1) shouldEqual(newFiles(i))
      }
    }
  }

  feature("InternalLog restarting") {
    val maxBufferSize = 256L
    val maxFileSize = maxBufferSize * 4L
    val maxRollFiles = 4

    scenario("sets the current file to the same as before if one non-full file present") {
      val oldLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      InternalLog.add(oldLog)
      overflow(oldLog)

      val oldPath = oldLog.currentPath
      val paths = pathsInTempDir
      paths.size shouldEqual(1)
      paths(0) shouldEqual(oldPath)

      InternalLog.reset()

      val newLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      newLog.currentPath shouldEqual(oldPath)
    }

    scenario("sets the current file to the same as before if if two files present") {
      val oldLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      InternalLog.add(oldLog)
      val oldPath = oldLog.currentPath
      (1 to 5).foreach( _ => overflow(oldLog) )
      val newPath = oldLog.currentPath
      newPath should not equal(oldPath)

      InternalLog.reset()

      val paths = pathsInTempDir
      paths.size shouldEqual(2)
      paths(0) shouldEqual(oldPath)
      paths(1) shouldEqual(newPath)

      val newLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      newLog.currentPath shouldEqual(newPath)
    }

    scenario("sets the current file to the same as before if more files present") {
      val oldLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      InternalLog.add(oldLog)
      val oldPath = oldLog.currentPath
      (1 to 5).foreach( _ => overflow(oldLog) )
      val newPath = oldLog.currentPath
      newPath should not equal(oldPath)

      (1 to 4).foreach( _ => overflow(oldLog) )
      val newerPath = oldLog.currentPath
      newerPath should not equal(oldPath)
      newerPath should not equal(newPath)

      (1 to 4).foreach( _ => overflow(oldLog) )
      val newestPath = oldLog.currentPath
      newestPath should not equal(oldPath)
      newestPath should not equal(newPath)
      newestPath should not equal(newerPath)

      InternalLog.reset()

      val paths = pathsInTempDir
      paths.size shouldEqual(4)
      paths(0) shouldEqual(oldPath)
      paths(1) shouldEqual(newPath)
      paths(2) shouldEqual(newerPath)
      paths(3) shouldEqual(newestPath)

      val newLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      newLog.currentPath shouldEqual(newestPath)
    }

    scenario("sets the current file to the same as before if maxFileSize grows") {
      val oldLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      InternalLog.add(oldLog)
      val oldPath = oldLog.currentPath
      (1 to 5).foreach( _ => overflow(oldLog) )
      val newPath = oldLog.currentPath
      (1 to 4).foreach( _ => overflow(oldLog) )
      val newerPath = oldLog.currentPath
      (1 to 4).foreach( _ => overflow(oldLog) )
      val newestPath = oldLog.currentPath

      InternalLog.reset()

      val newLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize * 2L, maxRollFiles)
      newLog.currentPath shouldEqual(newestPath)
    }

    scenario("sets the current file to the same as before if maxFileSize shrinks") {
      val oldLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      InternalLog.add(oldLog)
      val oldPath = oldLog.currentPath
      (1 to 5).foreach( _ => overflow(oldLog) )
      val newPath = oldLog.currentPath
      (1 to 4).foreach( _ => overflow(oldLog) )
      val newerPath = oldLog.currentPath
      (1 to 4).foreach( _ => overflow(oldLog) )
      val newestPath = oldLog.currentPath

      InternalLog.reset()

      val newLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize / 2L, maxRollFiles)
      newLog.currentPath shouldEqual(newestPath)
    }

    scenario("sets the paths to the same as before"){
      val oldLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      InternalLog.add(oldLog)
      (1 to 5).foreach( _ => overflow(oldLog) )
      (1 to 4).foreach( _ => overflow(oldLog) )
      (1 to 4).foreach( _ => overflow(oldLog) )

      val oldPaths = oldLog.getPaths
      val pathsInDir = pathsInTempDir
      oldPaths.toSet shouldEqual(pathsInDir.toSet)

      InternalLog.reset()

      val newLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      val newPaths = newLog.getPaths
      newPaths shouldEqual(oldPaths)
    }

    scenario("if maxFileSize shrinks old files are not affected"){
      val shrunkSize = maxFileSize / 2L
      val oldLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, maxRollFiles)
      InternalLog.add(oldLog)

      val oldPath = oldLog.currentPath
      while (new File(oldPath).length <= shrunkSize) overflow(oldLog)
      InternalLog.reset()

      val oldPaths = oldLog.getPaths
      oldPaths.size shouldEqual(1)
      oldPaths(0) shouldEqual(oldPath)
      val oldFileSize = new File(oldPath).length

      val newLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize / 2L, maxRollFiles)
      InternalLog.add(newLog)
      overflow(newLog)

      new File(oldPath).length shouldEqual(oldFileSize)
      newLog.getPaths.size shouldEqual(2)
      newLog.currentPath should not equal oldPath
      new File(newLog.currentPath).length() > 0 shouldEqual(true)
    }

    scenario("if maxRollFiles is changed to less than the current number of files, old ones are deleted"){
      val oldLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, 4)
      InternalLog.add(oldLog)
      (1 to 5).foreach( _ => overflow(oldLog) )
      (1 to 4).foreach( _ => overflow(oldLog) )
      (1 to 4).foreach( _ => overflow(oldLog) )
      InternalLog.reset()

      val oldPaths = oldLog.getPaths
      val pathsInDir = pathsInTempDir
      oldPaths.toSet shouldEqual(pathsInDir.toSet)

      oldPaths.size shouldEqual(4)
      val oldFirstFileStr = read(oldPaths(0))
      val thisShouldBeNewFirst = read(oldPaths(2))

      val newLog = new BufferedLogOutput(tempDir, maxBufferSize, maxFileSize, 2)
      newLog.getPaths shouldEqual(oldPaths) // we don't change anything until the next flush

      InternalLog.add(newLog)
      overflow(newLog)

      val newPaths = newLog.getPaths
      newPaths.size shouldEqual(2)

      val newFirstFileStr = read(newPaths(0))
      newFirstFileStr should not equal oldFirstFileStr
      newFirstFileStr shouldEqual(thisShouldBeNewFirst)
    }
  }
}
