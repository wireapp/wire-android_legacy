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

import java.io._
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.{GZIPOutputStream, ZipEntry, ZipInputStream, ZipOutputStream}

import com.waz.log.BasicLogging.LogTag
import com.waz.model.errors.FileSystemError
import com.waz.threading.CancellableFuture

import scala.annotation.tailrec
import scala.collection.Iterator.continually
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.control.NonFatal

trait Resource[Res] {
  def close(r: Res): Unit
}

object IoUtils {
  private val buffer = new ThreadLocal[Array[Byte]] {
    override def initialValue(): Array[Byte] = new Array[Byte](8096)
  }

  def createDirectory(file: File): Unit =
    if (!file.mkdirs() && !file.isDirectory)
      throw FileSystemError(s"Can not create directory: $file")

  def copy(in: InputStream, out: OutputStream): Long = {
    try {
      val buff = buffer.get()
      returning(continually(in read buff).takeWhile(_ != -1).map(returning(_)(out.write(buff, 0, _)).toLong).sum) { _ =>
        out match {
          case out: FileOutputStream =>
            out.flush()
            out.getFD.sync()
          case _ => // nothing to do
        }
      }
    } finally {
      // make sure both streams are closed
      var rethrow = None: Option[Throwable]
      try { out.close() } catch { case NonFatal(ex) => rethrow = Some(ex) }
      try { in.close() } catch { case NonFatal(ex) => rethrow = Some(ex) }
      rethrow foreach(throw _)
    }
  }

  def copy(in: InputStream, out: File): Long = {
    out.getParentFile.mkdirs()
    copy(in, new FileOutputStream(out))
  }

  def copy(in: File, out: File): Long = {
    out.getParentFile.mkdirs()
    copy(new FileInputStream(in), new BufferedOutputStream(new FileOutputStream(out)))
  }

  def copyAsync(in: => InputStream, out: File)(implicit ec: ExecutionContext): CancellableFuture[Long] =
    copyAsync(in, new BufferedOutputStream(new FileOutputStream(out)))

  def copyAsync(in: => InputStream, out: => OutputStream)(implicit ec: ExecutionContext): CancellableFuture[Long] = {
    val promise = Promise[Long]
    val cancelled = new AtomicBoolean(false)

    promise.trySuccess(copy(new CancellableStream(in, cancelled), out))

    new CancellableFuture(promise) {
      override def cancel()(implicit tag: LogTag): Boolean = cancelled.compareAndSet(false, true)
    }
  }

  def writeBytesToFile(file: File, bytes: Array[Byte]): Unit =
    withResource(new BufferedOutputStream(new FileOutputStream(file))) { f =>
      f.write(bytes)
    }

  // this method assumes that both streams will be properly closed outside
  @tailrec
  def write(in: InputStream, out: OutputStream, buff: Array[Byte] = buffer.get()): Unit =
    in.read(buff, 0, buff.length) match {
      case len if len > 0 =>
        out.write(buff, 0, len)
        write(in, out, buff)
      case _ =>
    }

  def writeZipEntry(in: InputStream, zip: ZipOutputStream, entryName: String): Unit = {
    zip.putNextEntry(new ZipEntry(entryName))
    write(in, zip)
    zip.closeEntry()
  }

  def toByteArray(in: InputStream) = {
    val out = new ByteArrayOutputStream()
    copy(in, out)
    out.toByteArray
  }

  def gzip(data: Array[Byte]) = {
    val bos = new ByteArrayOutputStream()

    withResource(new GZIPOutputStream(bos)) { os =>
      os.write(data)
      os.finish()
    }
    bos.toByteArray
  }

  def skip(is: InputStream, count: Long): Boolean = {
    val skipped = is.skip(count)
    if (skipped < 0) false
    else if (skipped < count) skip(is, count - skipped)
    else true
  }

  def readFileBytes(file: File, offset: Int = 0, byteCount: Option[Int] = None): Array[Byte] = {
    val outputSize = byteCount match {
      case Some(bc) => bc
      case None => file.length().toInt - offset
    }
    returning(Array.ofDim[Byte](outputSize)) { bytes =>
      withResource(new BufferedInputStream(new FileInputStream(file))) { f =>
        f.skip(offset)
        f.read(bytes)
      }
    }
  }

  def readFully(is: InputStream, buffer: Array[Byte], offset: Int, count: Int): Boolean = {
    val read = is.read(buffer, offset, count)
    if (read < 0) false
    else if (read == count) true
    else readFully(is, buffer, offset + read, count - read)
  }

  def readFully(is: InputStream, buffer: Array[Byte] = this.buffer.get()): Unit =
    withResource(is) { in =>
      while (in.read(buffer) != -1) { }
    }

  def asString(in: InputStream) = new String(toByteArray(in), "utf8")

  def withResource[I : Resource, O](in: I)(op: I => O): O = try op(in) finally implicitly[Resource[I]].close(in)
  def withResource[I <: Closeable, O](in: I)(op: I => O): O = try op(in) finally in.close()
  def withResources[I1 <: Closeable, I2 <: Closeable, O](in1: I1, in2: I2)(op: (I1, I2) => O): O =
    withResource(in1) { res1 => withResource(in2) { res2 => op(res1, res2) } }

  def counting[A](o: => OutputStream)(op: OutputStream => A): (Long, A) = {
    var written = 0L
    val delegate = o
    val out = new OutputStream {
      override def write(buffer: Array[Byte], offset: Int, length: Int): Unit = {
        delegate.write(buffer, offset, length)
        if (length > 0L) written += math.min(buffer.length - offset, length)
      }
      override def write(oneByte: Int): Unit = {
        delegate.write(oneByte)
        written += 1L
      }
      override def flush(): Unit = delegate.flush()
      override def close(): Unit = delegate.close()
    }
    try {
      val a = op(out)
      (written, a)
    } finally out.close()
  }

  def md5(file: File): Array[Byte] = hash(file, "MD5")
  def md5(stream: => InputStream): Array[Byte] = hash(stream, "MD5")
  def sha256(file: File): Array[Byte] = hash(file, "SHA-256")
  def sha256(is: InputStream): Array[Byte] = hash(is, "SHA-256")

  def hash(file: File, hashAlgorithm: String): Array[Byte] = hash(new FileInputStream(file), hashAlgorithm)

  def hash(stream: => InputStream, hashAlgorithm: String): Array[Byte] = withResource(stream) { in =>
    val digest = MessageDigest.getInstance(hashAlgorithm)
    val buff = buffer.get()
    continually(in read buff) takeWhile (_ != -1) foreach (size => digest.update(buff, 0, size))
    digest.digest()
  }

  def deleteRecursively(file: File): Unit =  {
    if (file.isDirectory)
      Option(file.listFiles()).foreach(_ foreach deleteRecursively)
    file.delete()
  }
}

class CancellableStream(stream: InputStream, cancelled: AtomicBoolean) extends FilterInputStream(stream) {
  override def read(buffer: Array[Byte], byteOffset: Int, byteCount: Int): Int = {
    if (cancelled.get) throw CancellableFuture.DefaultCancelException
    else super.read(buffer, byteOffset, byteCount)
  }

  override def read(): Int = {
    if (cancelled.get) throw CancellableFuture.DefaultCancelException
    else super.read()
  }
}
