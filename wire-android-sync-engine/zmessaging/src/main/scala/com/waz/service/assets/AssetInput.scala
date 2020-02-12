package com.waz.service.assets

import java.io.{BufferedInputStream, ByteArrayOutputStream, File, FileInputStream, InputStream}

import android.graphics.Bitmap.CompressFormat
import android.graphics.{BitmapFactory, Matrix}
import android.media.ExifInterface
import com.waz.model.{Dim2, Mime, Sha256}
import com.waz.model.errors.ValidationError
import com.waz.utils.{IoUtils, returning}
import com.waz.utils.wrappers.Bitmap
import android.graphics.{Bitmap => ABitmap}
import scala.util.{Failure, Success, Try}

sealed trait AssetInput {
  def validate(sha: Sha256): AssetInput
  def toInputStream: Try[InputStream]
  def toBitmap(opts: BitmapFactory.Options): Try[Bitmap]
  def toByteArray: Try[Array[Byte]]

  def toBitmap: Try[Bitmap] = toBitmap(null)
}

case class AssetStream(stream: InputStream) extends AssetInput {
  override def validate(sha: Sha256): AssetInput = Sha256.calculate(stream) match {
    case Success(sha256) if sha256 == sha =>
      stream.reset()
      this
    case Success(sha256) =>
      stream.close()
      AssetFailure(new ValidationError(s"SHA256 is not equal. Expected: $sha Actual: $sha256"))
    case Failure(throwable) =>
      stream.close()
      AssetFailure(throwable)
  }

  override def toInputStream: Try[InputStream] = Try(stream)

  override def toBitmap(opts: BitmapFactory.Options): Try[Bitmap] =
    Try {
      IoUtils.withResource(stream) { in => BitmapFactory.decodeStream(in, null, opts) }
    }

  override def toByteArray: Try[Array[Byte]] = Try { IoUtils.toByteArray(stream) }
}

case class AssetFile(file: File) extends AssetInput {
  override def validate(sha: Sha256): AssetInput =
    Try { IoUtils.readFileBytes(file) }.map(Sha256.calculate) match {
      case Success(sha256) if sha256 == sha =>
        this
      case Success(sha256) =>
        AssetFailure(new ValidationError(s"SHA256 is not equal. Expected: $sha Actual: $sha256"))
      case Failure(throwable) =>
        AssetFailure(throwable)
    }

  override def toInputStream: Try[InputStream] = Try { new BufferedInputStream(new FileInputStream(file)) }

  override def toBitmap(opts: BitmapFactory.Options): Try[Bitmap] = {
    val path = file.getAbsolutePath
    AssetInput.rotateIfNeeded(BitmapFactory.decodeFile(path, opts), path).map(Bitmap(_))
  }

  override def toByteArray: Try[Array[Byte]] = Try { IoUtils.readFileBytes(file) }
}

case class AssetFailure(throwable: Throwable) extends AssetInput {
  override def validate(sha: Sha256): AssetInput = this

  override def toInputStream: Try[InputStream] = Failure(throwable)

  override def toBitmap(opts: BitmapFactory.Options): Try[Bitmap] = Failure(throwable)

  override def toByteArray: Try[Array[Byte]]  = Failure(throwable)
}

object AssetInput {
  val BitmapOptions: BitmapFactory.Options = returning(new BitmapFactory.Options) {
    _.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
  }

  private val MaxImageDimension = 1448
  private val DefaultCompressionQuality = 75

  // set of mime types that should be recoded to Jpeg before uploading
  private val DefaultRecodeMimes = Set(
    Mime.Image.WebP,
    Mime.Image.Tiff,
    Mime.Image.Bmp,
    Mime.Unknown
  )

  def apply(stream: InputStream): AssetInput  = AssetStream(stream)
  def apply(file: File): AssetInput           = AssetFile(file)
  def apply(throwable: Throwable): AssetInput = AssetFailure(throwable)

  private def rotation(exif: ExifInterface): Int = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) match {
    case ExifInterface.ORIENTATION_ROTATE_90  => 90
    case ExifInterface.ORIENTATION_ROTATE_180 => 180
    case ExifInterface.ORIENTATION_ROTATE_270 => 270
    case _                                    => 0
  }

  private def compress(in: ABitmap): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    in.compress(CompressFormat.JPEG, DefaultCompressionQuality, out)
    out.toByteArray
  }

  def rotate(bitmap: ABitmap, rotation: Int): ABitmap = {
    val matrix = new Matrix()
    matrix.postRotate(rotation)
    ABitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth, bitmap.getHeight, matrix, true)
  }

  def currentRotation(path: String): Int = Try(rotation(new ExifInterface(path))).getOrElse(0)

  def rotateIfNeeded(bitmap: ABitmap, path: String): Try[ABitmap] = Try {
    val cr = currentRotation(path)
    if (cr == 0) bitmap else rotate(bitmap, cr)
  }

  def toJpg(bitmap: ABitmap): Content.Bytes = Content.Bytes(Mime.Image.Jpg, compress(bitmap))

  def bitmapOptions(scaleFactor: Int) = returning(new BitmapFactory.Options()) { opts =>
    opts.inJustDecodeBounds = false
    opts.inSampleSize = scaleFactor
    opts.inPreferredConfig = AssetInput.BitmapOptions.inPreferredConfig
  }

  def shouldScale(mime: Mime, dim: Dim2): Int =
    if (DefaultRecodeMimes.contains(mime) || mime != Mime.Image.Gif && (dim.height max dim.width) > MaxImageDimension)
      Math.max(dim.width / MaxImageDimension, dim.height / MaxImageDimension)
    else 0
}
