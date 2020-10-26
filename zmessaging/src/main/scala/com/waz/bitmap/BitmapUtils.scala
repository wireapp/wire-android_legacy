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
package com.waz.bitmap

import java.io.{BufferedInputStream, InputStream}

import android.graphics
import android.graphics.Bitmap.Config
import android.graphics._
import android.media.ExifInterface
import android.renderscript.{Allocation, Element, RenderScript, ScriptIntrinsicBlur}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.service.ZMessaging
import com.waz.utils.{IoUtils, returning}

object BitmapUtils extends DerivedLogTag {

  object Mime {
    val Gif = "image/gif"
    val Jpg = "image/jpeg"
    val Png = "image/png"
    val WebP = "image/webp"
    val Bmp = "image/bmp"
    val Tiff = "image/tiff"
    val Unknown = "image/*"
  }

  private val matrix = new ThreadLocal[Matrix] {
    override def initialValue(): Matrix = new Matrix()
  }
  
  private val localPaint = new ThreadLocal[Paint] {
    override def initialValue(): Paint = {
      val p = new Paint()
      p.setAntiAlias(true)
      p.setFilterBitmap(true)
      p
    }
  }

  def getPaint = {
    val p = localPaint.get()
    p.setColor(Color.TRANSPARENT)
    p.setShader(null)
    p
  }
  
  def createRoundBitmap(input: Bitmap, width: Int, borderWidth: Int, borderColor: Int): Bitmap = {
    debug(l"createRoundBitmap($width, $borderWidth)")

    val output: Bitmap = Bitmap.createBitmap(width, width, Config.ARGB_8888)
    val canvas: Canvas = new Canvas(output)
    val p = getPaint
    canvas.drawColor(Color.TRANSPARENT)

    if (borderWidth > 0) {
      p.setColor(borderColor)
      canvas.drawCircle(width / 2, width / 2, width / 2, p)
    }
    p.setColor(Color.RED)
    val shader = new BitmapShader(input, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    shader.setLocalMatrix(cropMatrix(input.getWidth, input.getHeight, width, width))
    p.setShader(shader)
    canvas.drawCircle(width / 2, width / 2, width / 2 - borderWidth, p)
    p.setShader(null)

    output
  }

  def cropMatrix(srcWidth: Int, srcHeight: Int, dstWidth : Int, dstHeight: Int): Matrix = {
    val mat = matrix.get()
    val scale = Math.max(dstWidth.toFloat / srcWidth, dstHeight.toFloat / srcHeight)
    mat.setScale(scale, scale)
    mat.postTranslate((dstWidth - srcWidth * scale) / 2, (dstHeight - srcHeight * scale) / 2)
    mat
  }

  def computeInSampleSize(minSize: Int, origSize: Int) =
    if (minSize >= origSize || minSize <= 0) 1
    else {
      var sample = 1
      var halfSize = origSize / 2
      while (halfSize >= minSize) {
        sample *= 2
        halfSize /= 2
      }
      sample
    }

  def cropRect(bitmap: Bitmap, width: Int) = {
    val m = matrix.get()
    val srcSize = bitmap.getWidth min bitmap.getHeight
    val scale = width.toFloat / srcSize
    m.setScale(scale, scale)

    Bitmap.createBitmap(bitmap, (bitmap.getWidth - srcSize) / 2, (bitmap.getHeight - srcSize) / 2, srcSize, srcSize, m, true)
  }

  def fixOrientationForUndefined(bitmap: Bitmap, orientation: Int): Bitmap = {
    val matrix = new Matrix()
    val rotation = getRotationForUndefined(bitmap)
    matrix.postRotate(rotation)
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth, bitmap.getHeight, matrix, true)
  }

  private def getRotationForUndefined(bitmap: Bitmap) : Float = if (bitmap.getWidth > bitmap.getHeight) 90f else 0f

  def fixOrientation(bitmap: Bitmap, orientation: Int): Bitmap = {
    import ExifInterface._

    if (orientation == ORIENTATION_NORMAL || orientation == ORIENTATION_UNDEFINED) bitmap else {
      val matrix = new Matrix
      orientation match {
        case ORIENTATION_ROTATE_180 =>      matrix.setRotate(180f)
        case ORIENTATION_ROTATE_90 =>       matrix.setRotate(90f)
        case ORIENTATION_ROTATE_270 =>      matrix.setRotate(270f)
        case ORIENTATION_FLIP_HORIZONTAL => matrix.setScale(-1f, 1f)
        case ORIENTATION_FLIP_VERTICAL =>   matrix.setScale(1f, -1f)
        case ORIENTATION_TRANSPOSE =>       matrix.setRotate(90f);  matrix.postScale(-1f, 1f)
        case ORIENTATION_TRANSVERSE =>      matrix.setRotate(-90f); matrix.postScale(-1f, 1f)
        case _ => error(l"unknown orientation $orientation encountered")
      }
      val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth, bitmap.getHeight, matrix, true)
      bitmap.recycle()
      corrected
    }
  }

  def getMime(bitmap: Bitmap) = bitmap.getConfig match {
    case Bitmap.Config.RGB_565 => Mime.Jpg
    case _ => Mime.Png
  }

  def getCompressFormat(mime: String) = mime match {
    case Mime.Png => Bitmap.CompressFormat.PNG
    case _ => Bitmap.CompressFormat.JPEG
  }

  def getOrientation(input: InputStream): Int = IoUtils.withResource(new BufferedInputStream(input)) { ExifOrientation(_) }

  def scale(bitmap: Bitmap, width: Int): Bitmap = scale(bitmap, width, bitmap.getHeight * width / bitmap.getWidth)

  def scale(bitmap: Bitmap, width: Int, height: Int): Bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

  val ImageHeaders = Seq(
//    Array(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Mime.Png,
    Array(0x89, 0x50, 0x4E) -> Mime.Png,
    Array(0xFF, 0xD8, 0xFF) -> Mime.Jpg,
//    Array(0x47, 0x49, 0x46, 0x38) -> Mime.Gif, // GIF
    Array(0x47, 0x49, 0x46) -> Mime.Gif, // GIF
//    Array(0x52, 0x49, 0x46, 0x46) -> Mime.WebP, // RIFF
    Array(0x52, 0x49, 0x46) -> Mime.WebP, // RIFF
    Array(0x49, 0x20, 0x49) -> Mime.Tiff,
    Array(0x49, 0x49, 0x2A, 0) -> Mime.Tiff,
    Array(0x42, 0x4D) -> Mime.Bmp
  )

  def detectImageType(in: InputStream): String = {
    val arr = new Array[Byte](3)
    in.read(arr)
    in.close()

    def headerMatches(header: Array[Int]) = header.zipWithIndex.forall(h => h._1 == arr(h._2))

    ImageHeaders.find(p => headerMatches(p._1)).fold(Mime.Unknown)(_._2)
  }

  lazy val renderScript = RenderScript.create(ZMessaging.context)

  def createBlurredBitmap(bitmap: Bitmap, width: Int, blurRadius: Int, blurPasses: Int): Bitmap = {

    val blur = returning(ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))){ _.setRadius(blurRadius) }

    val copiedBmp = bitmap.copy(bitmap.getConfig, true)

    val blurAlloc = Allocation.createFromBitmap(renderScript, copiedBmp)
    blur.setInput(blurAlloc)
    (0 until blurPasses).foreach{ _ =>
      blur.forEach(blurAlloc)
    }
    blurAlloc.copyTo(copiedBmp)
    blurAlloc.destroy()
    blur.destroy()

    copiedBmp
  }
}
