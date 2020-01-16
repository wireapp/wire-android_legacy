/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.assets

import java.io.{File, FileInputStream}
import java.net.URI

import android.content.Context
import android.media.{MediaExtractor, MediaMetadataRetriever}
import android.net.Uri
import com.waz.service.assets.{Content, PreparedContent}
import com.waz.utils._

import scala.util.Try

object MetadataExtractionUtils {

  type Source = Either[URI, File]

  def asSource(content: PreparedContent): Source = content match {
    case Content.File(_, file) => Right(file)
    case Content.Uri(uri) => Left(uri)
  }

  implicit val RetrieverCleanup: Cleanup[MediaMetadataRetriever] =
    new Cleanup[MediaMetadataRetriever] {
      override def apply(a: MediaMetadataRetriever): Unit = a.release()
    }

  implicit val ExtractorCleanup: Cleanup[MediaExtractor] =
    new Cleanup[MediaExtractor] {
      override def apply(a: MediaExtractor): Unit = a.release()
    }

  def createMetadataRetriever(source: Source)(implicit c: Context): Managed[MediaMetadataRetriever] =
    Managed(new MediaMetadataRetriever).map { retriever =>
      source match {
        case Left(uri) => retriever.setDataSource(c, Uri.parse(uri.toString))
        case Right(file) => retriever.setDataSource(new FileInputStream(file).getFD)
      }
      retriever
    }

  def createMediaExtractor(source: Source)(implicit c: Context): Managed[MediaExtractor] =
    Managed(new MediaExtractor).map { extractor =>
      source match {
        case Left(uri) => extractor.setDataSource(c, Uri.parse(uri.toString), null)
        case Right(file) => extractor.setDataSource(new FileInputStream(file).getFD)
      }
      extractor
    }

  def retrieve[A](key: Int, tag: String, convert: String => A)
                 (implicit retriever: MediaMetadataRetriever): Option[A] =
    Option(retriever.extractMetadata(key)) match {
      case None    => None
      case Some(s) => Try(convert(s)).toOption
    }
}
