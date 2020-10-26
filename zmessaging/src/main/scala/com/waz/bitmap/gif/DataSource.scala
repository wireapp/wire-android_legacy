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
package com.waz.bitmap.gif

import java.io._
import java.nio.{ByteBuffer, ByteOrder}

import com.waz.bitmap.gif.Gif.{Frame, FrameDataSource}
import com.waz.utils.IoUtils

trait DataSource {
  var blockSize = 0
  val block = new Array[Byte](256)

  /**
   * Reads a single byte from input.
   */
  def read(): Int

  /**
   * Reads next 16-bit value, LSB first
   */
  def readShort(): Int

  def position: Int

  def isEmpty: Boolean

  def close(): Unit = {}

  /**
   * Reads up to 'count' bytes from input and stores them in buffer at given offset.
   * @return number of bytes read
   */
  def read(buffer: Array[Byte], offset: Int, count: Int): Int

  def readFully(buffer: Array[Byte], offset: Int, count: Int): Unit = {
    var total = 0
    while (total < count) {
      val c = read(buffer, offset + total, count - total)
      if (c < 0) throw new IOException("No more data available")
      total += c
    }
  }

  def readFully(buffer: ByteBuffer, count: Int): Unit = {
    val arr = Array.ofDim[Byte](4096)
    var total = 0
    while (total < count) {
      val c = read(arr, 0, math.min(arr.length, count - total))
      if (c < 0) throw new IOException("No more data available")
      buffer.put(arr, 0, c)
      total += c
    }
  }

  /**
   * Skips up to 'count' bytes from input.
   * @return number of bytes skipped
   */
  def skipBytes(count: Int): Int

  def skipFully(count: Int): Boolean = {
    var n = count
    while (n > 0) {
      val c = skipBytes(n)
      if (c < 0) return false //TODO remove return
      n -= c
    }
    true
  }

  /**
   * Reads next variable length block from input.
   *
   * @return number of bytes stored in "block" buffer
   */
  def readBlock(): Int = {
    blockSize = read()
    readFully(block, 0, blockSize)
    blockSize
  }

  def readBlock[A](f: (Int, Array[Byte]) => A): A = {
    blockSize = read()
    readFully(block, 0, blockSize)
    f(blockSize, block)
  }

  def readBlocks[A](f: (Int, Array[Byte]) => A) = Iterator.continually(readBlock(f))

  /**
   * Skips next variable length block from input.
   * @return number of bytes skipped (size of skipped block)
   */
  def skipBlock(): Int = {
    blockSize = read()
    if (skipFully(blockSize)) blockSize else -1
  }

  /**
   * Skips variable length blocks up to and including next zero length block.
   */
  def skip(): Unit = while (skipBlock() > 0) {}
}

class ByteBufferDataSource(byteBuffer: ByteBuffer) extends DataSource {

  override def position: Int = byteBuffer.position()

  /**
   * Reads next 16-bit value, LSB first
   */
  override def readShort(): Int = byteBuffer.getShort

  /**
   * Reads a single byte from input.
   */
  override def read(): Int = byteBuffer.get & 0xFF

  override def isEmpty: Boolean = byteBuffer.position() >= byteBuffer.limit()

  /**
   * Reads up to 'count' bytes from input and stores them in buffer at given offset.
   * @return number of bytes read
   */
  override def read(buffer: Array[Byte], offset: Int, count: Int): Int =
    if (byteBuffer.capacity() < byteBuffer.position() + count) -1
    else {
      byteBuffer.get(buffer, offset, count)
      count
    }

  /**
   * Skips up to 'count' bytes from input.
   * @return number of bytes skipped
   */
  override def skipBytes(count: Int): Int = {
    byteBuffer.position(byteBuffer.position() + count)
    count
  }
}

class ByteArrayDataSource(data: Array[Byte]) extends ByteBufferDataSource(ByteArrayDataSource.wrap(data))

object ByteArrayDataSource {

  def wrap(data: Array[Byte]) = {
    val byteBuffer = ByteBuffer.wrap(data)
    byteBuffer.rewind
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer
  }
}

class ByteArrayFrameDataSource(data: Array[Byte]) extends FrameDataSource {

  val buffer = ByteArrayDataSource.wrap(data)
  val dataSource = new ByteBufferDataSource(buffer)

  override def apply(frame: Frame): DataSource = {
    buffer.clear()
    buffer.position(frame.bufferFrameStart)
    buffer.limit(frame.bufferFrameStart + frame.imageDataSize)
    dataSource
  }
}

class FileDataSource(file: File) extends StreamDataSource(new FileInputStream(file))

class StreamDataSource(stream: InputStream) extends DataSource {

  val reader = new BufferedInputStream(stream)
  var pos = 0

  /**
   * Reads a single byte from input.
   */
  override def read(): Int = {
    pos += 1
    reader.read()
  }

  override def position: Int = pos

  /**
   * Reads next 16-bit value, LSB first
   */
  override def readShort(): Int = {
    pos += 2
    reader.read | (reader.read() << 8)
  }

  /**
   * Reads up to 'count' bytes from input and stores them in buffer at given offset.
   * @return number of bytes read
   */
  override def read(buffer: Array[Byte], offset: Int, count: Int): Int = {
    val bytes = reader.read(buffer, offset, count)
    if (bytes > 0) pos += bytes
    bytes
  }

  /**
   * Skips up to 'count' bytes from input.
   * @return number of bytes skipped
   */
  override def skipBytes(count: Int): Int = {
    val bytes = reader.skip(count)
    pos += bytes.toInt
    bytes.toInt
  }

  override def isEmpty: Boolean = reader.available() > 0

  override def close(): Unit = reader.close()
}

class FileFrameDataSource(file: File) extends StreamFrameDataSource(() => new FileInputStream(file))

class StreamFrameDataSource(stream: () => InputStream) extends FrameDataSource {

  var position = Int.MaxValue
  var input = None: Option[InputStream]

  override def apply(frame: Frame): DataSource = {
    if (input.isEmpty || position > frame.bufferFrameStart) { position = 0
      input.foreach(_.close())
      input = Some(new BufferedInputStream(stream()))
    }
    val buffer = new Array[Byte](frame.imageDataSize)
    input.foreach { is =>
      if (IoUtils.skip(is, frame.bufferFrameStart - position) && IoUtils.readFully(is, buffer, 0, buffer.length))
        position = frame.bufferFrameStart + buffer.length
      else
        position = Int.MaxValue // something went wrong, will restart from new stream next time
    }
    new ByteArrayDataSource(buffer)
  }

  override def close() = {
    input.foreach(_.close)
    input = None
  }
}
