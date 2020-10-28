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
package com.waz.cache

import java.io._

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow
import com.waz.log.LogSE._
import com.waz.model.CacheKey
import com.waz.utils.IoUtils

import scala.concurrent.{ExecutionContext, Future}

/**
 * Common interface to access locally available data.
 * Unifies access to file, in memory byte array and cache entry.
 */
trait LocalData {
  def file: Option[File] = None
  def byteArray: Option[Array[Byte]] = None
  def delete(): Unit = ()

  def inputStream: InputStream
  def length: Int
}

object LocalData {
  case object Empty extends LocalData {
    override def inputStream: InputStream = new ByteArrayInputStream(Array.empty)
    override def length: Int = 0
  }

  def apply(file: File): LocalData = new LocalFile(file)
  def apply(bytes: Array[Byte]): LocalData = new ArrayData(bytes)
  def apply(stream: => InputStream, len: Int): LocalData = new LocalStream(() => stream, len)

  private class LocalFile(f: File) extends LocalData {
    override def inputStream = new BufferedInputStream(new FileInputStream(f))
    override lazy val length = f.length().toInt
    override def file = Some(f)
    override def delete() = f.delete()

    override def equals(o: scala.Any): Boolean = o match {
      case lf: LocalFile => file == lf.file
      case _ => false
    }
  }

  private class LocalStream(stream: () => InputStream, len: Int) extends LocalData {
    override def length = len
    override def inputStream: InputStream = stream()
  }

  private class ArrayData(bytes: Array[Byte]) extends LocalData {
    override def byteArray: Option[Array[Byte]] = Some(bytes)
    override def inputStream: InputStream = new ByteArrayInputStream(bytes)
    override def length: Int = bytes.length
  }
}

//Basically masks a CacheEntryData so that it can be treated like any other form of LocalData
class CacheEntry(val data: CacheEntryData, service: CacheService) extends LocalData with DerivedLogTag {

  override def inputStream: InputStream =
    content.fold[InputStream](CacheService.inputStream(data.encKey, new FileInputStream(cacheFile)))(new ByteArrayInputStream(_))

  override def length: Int = content.map(_.length.toLong).orElse(data.length).getOrElse(cacheFile.length).toInt

  override def file = content.fold(Option(cacheFile))(_ => None)

  override def byteArray = content

  def content = data.data

  // direct access to this file is not advised, it's content will be encrypted when on external storage, it's better to use stream api
  private[waz] def cacheFile = service.entryFile(data.path.getOrElse(service.cacheDir), data.fileId)

  def outputStream = {
    cacheFile.getParentFile.mkdirs()
    CacheService.outputStream(data.encKey, new FileOutputStream(cacheFile))
  }

  def copyDataToFile() = {
    content foreach { data =>
      IoUtils.copy(new ByteArrayInputStream(data), outputStream)
    }
    cacheFile
  }

  override def delete(): Unit = service.remove(this)

  override def toString: String = s"CacheEntry($data)"

  def updatedWithLength(len: Long)(implicit ec: ExecutionContext): Future[CacheEntry] = service.insert(new CacheEntry(data.copy(length = Some(len)), service))
}

object CacheEntry {

  implicit val CacheEntryLogShow: LogShow[CacheEntry] =
    LogShow.createFrom { e =>
      l"CacheEntry: data: ${e.data}"
    }

  def unapply(entry: CacheEntry): Option[(CacheKey, Option[Array[Byte]], File)] = Some((entry.data.key, entry.content, entry.cacheFile))
}
