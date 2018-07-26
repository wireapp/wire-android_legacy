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
import java.text.DecimalFormat

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.cache2.CacheService.Encryption
import com.waz.model.AESKey
import com.waz.model.errors.NotFoundLocal
import com.waz.utils.IoUtils
import com.waz.utils.crypto.AESUtils
import com.waz.utils.events._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait CacheService {
  implicit def ec: ExecutionContext

  protected def getOutputStream(key: String): OutputStream
  protected def getInputStream(key: String): Option[InputStream]

  def putEncrypted(key: String, file: File): Future[Unit]
  def remove(key: String): Future[Unit]

  def find(key: String)(encryption: Encryption): Future[Option[InputStream]] =
    Future(getInputStream(key).map(encryption.decrypt))
  def get(key: String)(encryption: Encryption): Future[InputStream] =
    failedIfEmpty(key, find(key)(encryption))
  def put(key: String, in: InputStream)(encryption: Encryption): Future[Unit] =
    Future(IoUtils.copy(in, encryption.encrypt(getOutputStream(key))))

  def findBytes(key: String)(encryption: Encryption): Future[Option[Array[Byte]]] =
    find(key)(encryption).map(_.map(IoUtils.toByteArray))
  def getBytes(key: String)(encryption: Encryption): Future[Array[Byte]] =
    failedIfEmpty(key, findBytes(key)(encryption))
  def putBytes(key: String, bytes: Array[Byte])(encryption: Encryption): Future[Unit] =
    put(key, new ByteArrayInputStream(bytes))(encryption)

  private def failedIfEmpty[T](key: String, value: Future[Option[T]]): Future[T] =
    value.flatMap {
      case Some(is) => Future.successful(is)
      case None => Future.failed(NotFoundLocal(s"Cache with key = '$key' not found."))
    }

}

object CacheService {

  trait Encryption {
    def decrypt(is: InputStream): InputStream
    def encrypt(os: OutputStream): OutputStream
  }

  case object NoEncryption extends Encryption {
    override def decrypt(is: InputStream): InputStream   = is
    override def encrypt(os: OutputStream): OutputStream = os
  }

  case class AES_CBC_Encryption(key: AESKey) extends Encryption {
    override def decrypt(is: InputStream): InputStream   = AESUtils.inputStream(key, is)
    override def encrypt(os: OutputStream): OutputStream = AESUtils.outputStream(key, os)
  }

}

object LoggingUtils {

  def formatSize(size: Long): String = {
    if (size <= 0) return "0"
    val units       = Array[String]("B", "kB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size) / Math.log10(1024)).toInt
    new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units(digitGroups)
  }

}

class LruFileCacheServiceImpl(
    cacheDirectory: File,
    directorySizeThreshold: Long,
    sizeCheckingInterval: FiniteDuration
)(implicit override val ec: ExecutionContext, ev: EventContext)
    extends CacheService {

  private val directorySize: SourceSignal[Long] = Signal()
  directorySize
    .throttle(sizeCheckingInterval)
    .filter { size =>
      verbose(s"Current cache size: ${LoggingUtils.formatSize(size)}")
      size > directorySizeThreshold
    } { size =>
      var shouldBeCleared = size - directorySizeThreshold
      verbose(s"Cache directory size threshold reached. Current size: ${LoggingUtils.formatSize(size)}. " +
        s"Should be cleared: ${LoggingUtils.formatSize(shouldBeCleared)}")
      cacheDirectory
        .listFiles()
        .sortBy(_.lastModified())
        .takeWhile { file =>
          val fileSize = file.length()
          if (file.delete()) {
            verbose(s"File '${file.getName}' removed. Cleared ${LoggingUtils.formatSize(fileSize)}.")
            shouldBeCleared -= fileSize
          } else {
            verbose(s"File '${file.getName}' can not be removed. Not cleared ${LoggingUtils.formatSize(fileSize)}.")
          }
          shouldBeCleared > 0
        }
    }

  updateDirectorySize()

  def updateDirectorySize(): Unit =
    Future(cacheDirectory.listFiles().foldLeft(0L)(_ + _.length())).foreach(size => directorySize ! size)

  private def getFile(key: String): File = new File(cacheDirectory, key)

  override protected def getOutputStream(key: String): OutputStream = {
    val file = getFile(key)
    file.createNewFile()
    new FileOutputStream(file)
  }

  override protected def getInputStream(key: String): Option[InputStream] = {
    val file = getFile(key)
    if (file.exists()) {
      file.setLastModified(System.currentTimeMillis())
      Some(new FileInputStream(file))
    } else None
  }

  override def put(key: String, in: InputStream)(encryption: Encryption): Future[Unit] =
    super.put(key, in)(encryption).map(_ => updateDirectorySize())


  override def remove(key: String): Future[Unit] = Future { getFile(key).delete() }

  def putEncrypted(key: String, file: File): Future[Unit] = Future {
    val targetFile = new File(cacheDirectory, key)
    if (targetFile.exists()) targetFile.delete()
    file.setLastModified(System.currentTimeMillis())
    file.renameTo(targetFile)
    updateDirectorySize()
  }

}
