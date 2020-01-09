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
package com.waz.service.assets

import java.io.{InputStream, OutputStream}

import com.waz.model.Mime
import Asset.General
import AssetTransformationsService._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._

trait AssetTransformationsService {
  def getTransformations(mime: Mime, details: AssetDetails): List[Transformation]
}

object AssetTransformationsService {

  trait Transformation {
    def apply(initial: () => InputStream, transformed: () => OutputStream): Mime
  }

  object Transformation {
    def create(f: (() => InputStream, () => OutputStream) => Mime): Transformation = new Transformation {
      override def apply(initial: () => InputStream, transformed: () => OutputStream): Mime = f(initial, transformed)
    }
  }

  trait Handler {
    def createTransformation(mime: Mime, details: AssetDetails): Option[Transformation]
  }

}

class AssetTransformationsServiceImpl(handlers: List[Handler]) extends AssetTransformationsService {

  override def getTransformations(mime: Mime, details: AssetDetails): List[Transformation] = {
    handlers.map(_.createTransformation(mime, details)).collect { case Some(t) => t }
  }

}

class ImageDownscalingCompressing(imageRecoder: ImageRecoder) extends Handler with DerivedLogTag {

  private val MaxImageDimension = 1448

  // set of mime types that should be recoded to Jpeg before uploading
  val DefaultRecodeMimes = Set(
    Mime.Image.WebP,
    Mime.Image.Tiff,
    Mime.Image.Bmp,
    Mime.Unknown
  )

  override def createTransformation(mime: Mime, details: General): Option[Transformation] = {
    Some(details)
      .collect { case ImageDetails(dim) => dim }
      .filter(dim => DefaultRecodeMimes.contains(mime) ||
        mime != Mime.Image.Gif && (dim.height max dim.width) > MaxImageDimension)
      .map { dim =>
        val targetMime = mime match {
          case Mime.Image.Png => Mime.Image.Png
          case _ => Mime.Image.Jpg
        }

        verbose(l"Creating asset image downscaling and compression transformation. $mime -> $targetMime")
        Transformation.create { (in, out) =>
          imageRecoder.recode(dim, targetMime, MaxImageDimension, in, out)
          targetMime
        }
      }
  }

}
