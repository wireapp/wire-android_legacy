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

import java.io.{InputStream, File}
import java.util.concurrent.TimeUnit

import com.waz.bitmap.gif.Gif._
import com.waz.bitmap.gif.GifReader.{FormatException, GraphicControlExtension}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try

class GifReader(input: DataSource, frameData: FrameDataSource) {

  var width: Int = 0
  var height: Int = 0
  var gctFlag: Boolean = false
  var gctSize: Int = 0
  var gct: Array[Int] = Array.empty
  var bgIndex: Int = 0
  var pixelAspect: Int = 0
  var loopCount: Gif.Loop = Loop.None
  var frames = new ListBuffer[Frame]()

  /**
   * Reads GIF image from input
   */
  def read(): Try[Gif] = Try {
    readHeader()
    readContents()
    input.close()
    Gif(width, height, frames.toArray, frameData, loopCount, gct, bgIndex, pixelAspect)
  }

  /**
   * Reads GIF file header information.
   */
  def readHeader() = {
    if (input.read() != 'G' || input.read() != 'I' || input.read() != 'F') throw FormatException
    input.skipFully(3) // id consists of 6 bytes
    readLSD()
    if (gctFlag) {
      gct = readColorTable(gctSize)
    }
  }

  /**
   * Reads Logical Screen Descriptor
   */
  def readLSD() = {
    width = input.readShort()
    height = input.readShort()
    val packed = input.read()
    gctFlag = (packed & 0x80) != 0
    gctSize = 2 << (packed & 7)
    bgIndex = input.read()
    pixelAspect = input.read()
  }

  private val colorsBuffer = new Array[Byte](256 * 3) // private buffer used to load color table

  /**
   * Reads color table as 256 RGB integer values
   *
   * @param ncolors int number of colors to read
   * @return int array containing 256 colors (packed ARGB with full alpha)
   */
  def readColorTable(ncolors: Int): Array[Int] = {
    input.readFully(colorsBuffer, 0, 3 * ncolors)

    Array.tabulate(256) { i =>
      if (i >= ncolors) 0
      else {
        val x = i * 3
        val r = colorsBuffer(x) & 0xff
        val g = colorsBuffer(x + 1) & 0xff
        val b = colorsBuffer(x + 2) & 0xff
        0xff000000 | (r << 16) | (g << 8) | b
      }
    }
  }

  /**
   * Main file parser. Reads GIF content blocks.
   */
  def readContents() = {
   
    def isNetscapeExt(size: Int, block: Array[Byte]) = {
      size >= 11 && block.toSeq.take(11) == GifReader.NetscapeExtLabel
    }
    
    var done = false
    var gce = GraphicControlExtension.Default
    
    while (!done) {
      input.read() match {
        case 0x2C => // image separator
          frames += readFrame(gce)
          gce = GraphicControlExtension.Default

        case 0x21 => //extension
          input.read() match {
            case 0xf9 => // graphic control extension
              gce = readGraphicControlExt()
            case 0xff => //application extension
              if (input.readBlock(isNetscapeExt)) loopCount = readLoopCount()
              else input.skip() // don't care
            case _ => // any other extension
              input.skip()
          }
        case 0x3b => // terminator
          done = true
        case 0x00 => // bad byte, but keep going and see what happens
        case _ =>
          throw FormatException
      }
    }
  }


  /**
   * Reads Graphics Control Extension values
   */
  def readGraphicControlExt() = {
    def roundDelay(delay: Int) = if (delay < 20) 100 else delay // using similar settings as chrome and firefox http://nullsleep.tumblr.com/post/16524517190/animated-gif-minimum-frame-delay-browser

    input.read() // block size
    val packed = input.read() // packed fields
    val dispose = Disposal((packed & 0x1c) >> 2)
    val transparency = (packed & 1) != 0
    val delay = FiniteDuration(roundDelay(input.readShort() * 10), TimeUnit.MILLISECONDS) // delay in milliseconds
    val transIndex = input.read() // transparent color index
    input.read() // block terminator
    
    GraphicControlExtension(dispose, delay, transparency, transIndex)
  }

  /**
   * Reads next frame image
   */
  def readFrame(gce: GraphicControlExtension) = {
    val bounds = Bounds(input.readShort(), input.readShort(), input.readShort(), input.readShort()).fit(width, height)
    val packed = input.read()
    val lctFlag = (packed & 0x80) != 0
    val lctSize = 2 << (packed & 0x07)
    val interlace = (packed & 0x40) != 0
    val lct = if (lctFlag) { readColorTable(lctSize) } else Array.empty[Int]
    
    val bufferFrameStart = input.position
    skipBitmapData()
    val imageDataSize = input.position - bufferFrameStart
    
    Frame(bounds, interlace, gce.transparency, gce.dispose, gce.transIndex, gce.delay, bufferFrameStart, imageDataSize, lct)
  }

  def skipBitmapData() = {
    input.read()
    input.skip()
  }

  /**
   * Reads Netscape extenstion to obtain iteration count
   */
  def readLoopCount() = {

    def loopCount(size: Int, block: Array[Byte]) =
      if (size == 0) None
      else if (block(0) == 1) Some(Loop(GifReader.readShort(block, 1)))
      else Some(Loop.Unknown)

    input.readBlocks(loopCount)
      .takeWhile(_.isDefined)
      .filter(_ != Some(Loop.Unknown))
      .flatten.toList.headOption.getOrElse(Loop.None)
  }
}

object GifReader {
  val NetscapeExtLabel = Seq('N', 'E', 'T', 'S', 'C', 'A', 'P', 'E', '2', '.', '0')

  case object FormatException extends Exception("Could not read gif input. Wrong format.")

  case class GraphicControlExtension(
                                      dispose: Disposal = Disposal.None,
                                      delay: FiniteDuration = 25.millis, // Delay to next frame
                                      transparency: Boolean = false,
                                      transIndex: Int = 0 // Transparency Index
                                    )

  object GraphicControlExtension {
    val Default = new GraphicControlExtension()
  }

  def apply(data: Array[Byte]): Try[Gif] = new GifReader(new ByteArrayDataSource(data), new ByteArrayFrameDataSource(data)).read()

  def apply(file: File): Try[Gif] = new GifReader(new FileDataSource(file), new FileFrameDataSource(file)).read()

  def apply(stream: => InputStream): Try[Gif] = new GifReader(new StreamDataSource(stream), new StreamFrameDataSource(() => stream)).read()

  /**
   * Reads 16-bit value in buffer at given offset, LSB first
   */
  def readShort(buffer: Array[Byte], offset: Int): Int =
    (buffer(offset) & 0xff) | ((buffer(offset + 1) & 0xff) << 8)


}
