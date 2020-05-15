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
package com.waz.cache2

import java.io._

import com.waz.log.LogSE._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.errors.{FailedExpectationsError, FileSystemError, NotFoundLocal, ZError}
import com.waz.utils.IoUtils
import com.waz.utils.events._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait FileCache[K] {
  def exists(key: K): Future[Boolean]
  def remove(key: K): Future[Unit]

  def put(key: K, file: File, removeOriginal: Boolean = false): Future[Unit]
  def putStream(key: K, in: InputStream): Future[Unit]
  def putBytes(key: K, bytes: Array[Byte]): Future[Unit]

  def find(key: K): Future[Option[File]]
  def findStream(key: K): Future[Option[InputStream]]
  def findBytes(key: K): Future[Option[Array[Byte]]]

  def get(key: K): Future[File]
  def getOrCreateEmpty(key: K): Future[File]
  def getStream(key: K): Future[InputStream]
  def getBytes(key: K): Future[Array[Byte]]

  def getFileSize(key: K): Future[Long]
  def createEmptyFile(key: K): Future[File]
  def changeKey(oldKey: K, newKey: K): Future[Unit]
}

abstract class BaseFileCache[K] extends FileCache[K] with DerivedLogTag {
  import BaseFileCache._

  protected implicit def ec: ExecutionContext

  protected def failedIfEmpty[T](key: K, value: Future[Option[T]]): Future[T] =
    value.flatMap {
      case Some(is) => Future.successful(is)
      case None => Future.failed(keyNotFoundError(createFileName(key)))
    }

  protected def createFileName(key: K): String
  protected def createFile(key: K): File

  protected def getOperationHook(key: K, targetFile: File): Unit = ()
  protected def putOperationHook(key: K, targetFile: File): Unit = ()

  override def exists(key: K): Future[Boolean] =
    Future { createFile(key).exists() }

  override def put(key: K, file: File, removeOriginal: Boolean = false): Future[Unit] =
    Future {
      val targetFile = createFile(key)
      def copy(): Unit = {
        debug(l"Copy $file to $targetFile")
        IoUtils.copy(file, targetFile)
      }
      if (removeOriginal) {
        debug(l"Renaming $file to $targetFile")
        if (!file.renameTo(targetFile)) {
          debug(l"Renaming failed. Falling back to copy.")
          copy()
          file.delete()
        }
      } else copy()

      if (!targetFile.exists()) throw FailedExpectationsError(s"$targetFile does not exist after put operation")
      putOperationHook(key, targetFile)
    }

  override def putStream(key: K, in: InputStream): Future[Unit] =
    Future {
      val targetFile = createFile(key)
      IoUtils.copy(in, targetFile)
      putOperationHook(key, targetFile)
    }

  override def putBytes(key: K, bytes: Array[Byte]): Future[Unit] =
    putStream(key, new ByteArrayInputStream(bytes))

  override def find(key: K): Future[Option[File]] =
    Future {
      val targetFile = createFile(key)
      getOperationHook(key, targetFile)
      if (!targetFile.exists()) None
      else Some(targetFile)
    }

  override def get(key: K): Future[File] =
    failedIfEmpty(key, find(key))

  override def getOrCreateEmpty(key: K): Future[File] = get(key).recoverWith {
    case _: NotFoundLocal => createEmptyFile(key)
    case err => Future.failed(err)
  }

  override def findStream(key: K): Future[Option[InputStream]] =
    find(key).map(_.map(new FileInputStream(_)))

  override def getStream(key: K): Future[InputStream] =
    failedIfEmpty(key, findStream(key))

  override def findBytes(key: K): Future[Option[Array[Byte]]] =
    findStream(key).map(_.map(IoUtils.toByteArray))

  override def getBytes(key: K): Future[Array[Byte]] =
    failedIfEmpty(key, findBytes(key))

  override def remove(key: K): Future[Unit] =
    Future { createFile(key).delete() }

  override def getFileSize(key: K): Future[Long] =
    Future { createFile(key).length() }

  override def createEmptyFile(key: K): Future[File] =
    for {
      targetFile <- Future(createFile(key))
      _ <- if (targetFile.exists()) Future.failed(keyAlreadyExistError(createFileName(key)))
           else Future.successful(targetFile.createNewFile())
    } yield targetFile

  override def changeKey(oldKey: K, newKey: K): Future[Unit] =
    for {
      oldFile <- Future(createFile(oldKey))
      _ <- if (oldFile.exists()) Future.successful(()) else Future.failed(keyNotFoundError(createFileName(oldKey)))
      newFile = createFile(newKey)
      _ <- if (newFile.exists()) Future.failed(keyAlreadyExistError(createFileName(newKey))) else Future.successful(())
      renamingResult = oldFile.renameTo(newFile)
      _ <- if (renamingResult) Future.successful(()) else Future.failed(FileSystemError(s"Can not rename $oldFile to $newFile"))
    } yield ()

}

object BaseFileCache {

  trait Key[T] {
    def fileName(value: T): String
  }

  def keyNotFoundError(key: String): ZError =
    NotFoundLocal(s"Cache with key = '$key' not found.")
  def keyAlreadyExistError(key: String): ZError =
    FailedExpectationsError(s"Cache with key = '$key' already exist.")

}

abstract class LruFileCache[K] extends BaseFileCache[K] {

  protected def cacheDirectory: File
  protected def directorySizeThreshold: Long
  protected def sizeCheckingInterval: FiniteDuration

  protected implicit def ev: EventContext

  private val directorySize: SourceSignal[Long] = Signal()
  directorySize
    .throttle(sizeCheckingInterval)
    .filter { size =>
      verbose(l"Current cache size: ${asSize(size)}")
      size > directorySizeThreshold
    } { size =>
      var shouldBeCleared = size - directorySizeThreshold
      verbose(l"Cache directory size threshold reached. Current size: ${asSize(size)}. Should be cleared: ${asSize(shouldBeCleared)}")
      cacheDirectory
        .listFiles()
        .sortBy(_.lastModified())
        .takeWhile { file =>
          val fileSize = file.length()
          if (file.delete()) {
            verbose(l"File '$file' removed. Cleared ${asSize(fileSize)}.")
            shouldBeCleared -= fileSize
          } else {
            verbose(l"File '$file' can not be removed. Not cleared ${asSize(fileSize)}.")
          }
          shouldBeCleared > 0
        }
    }

  updateDirectorySize()

  private def updateDirectorySize(): Unit =
    Future(cacheDirectory.listFiles().foldLeft(0L)(_ + _.length())).foreach(size => directorySize ! size)

  override protected def createFile(key: K): File =
    new File(cacheDirectory, createFileName(key))

  override protected def getOperationHook(key: K, targetFile: File): Unit =
    if (targetFile.exists()) targetFile.setLastModified(System.currentTimeMillis())

  override protected def putOperationHook(key: K, targetFile: File): Unit =
    updateDirectorySize()

}

abstract class SimpleFileCache[K] extends BaseFileCache[K] {

  protected def cacheDirectory: File

  override protected def createFile(key: K): File =
    new File(cacheDirectory, createFileName(key))

}
