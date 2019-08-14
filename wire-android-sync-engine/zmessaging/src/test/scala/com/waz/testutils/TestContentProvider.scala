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
package com.waz.testutils

import android.content.{ContentProvider, ContentValues}
import android.database.{Cursor, MatrixCursor}
import android.net.Uri
import android.provider.OpenableColumns._
import android.webkit.MimeTypeMap
import com.waz.model.Mime
import com.waz.utils
import com.waz.utils.wrappers.{AndroidURI, URI}
import com.waz.utils.IoUtils
import org.robolectric.Robolectric._
import org.robolectric.shadows.ShadowContentResolver2

import scala.util.Try

trait TestContentProvider extends ContentProvider {
  override def getType(uri: Uri): String = ""
  override def update(uri: Uri, contentValues: ContentValues, s: String, strings: Array[String]): Int = 0
  override def insert(uri: Uri, contentValues: ContentValues): Uri = null
  override def delete(uri: Uri, s: String, strings: Array[String]): Int = 0
  override def onCreate(): Boolean = true

  def cursor(columns: Vector[String], values: TraversableOnce[TraversableOnce[String]]): Cursor =
    utils.returning(new MatrixCursor(columns.toArray)) { c =>
      values foreach { row =>
        val rowArr = row.toArray[AnyRef]
        require(rowArr.length == columns.size, "number of columns did not match")
        c.addRow(rowArr)
      }
    }
}

class TestResourceContentProvider(val authority: String = "com.waz.testresources") extends TestContentProvider {
  val mimeMap = shadowOf(MimeTypeMap.getSingleton)
  mimeMap.addExtensionMimeTypMapping("pdf", "application/pdf")
  mimeMap.addExtensionMimeTypMapping("png", "image/png")
  mimeMap.addExtensionMimeTypMapping("jpg", "image/jpg")
  mimeMap.addExtensionMimeTypMapping("mp4", Mime.Video.MP4.str)
  mimeMap.addExtensionMimeTypMapping("m4a", Mime.Audio.MP4.str)

  case class Resource(uri: URI, mime: Mime, size: Long) {
    def inputStream = getClass.getResourceAsStream(uri.getPath)
    def registerStream() = resolver.registerInputStream(URI.unwrap(uri), () => inputStream)
    def isEmpty = uri.toString.isEmpty
    def name = uri.getLastPathSegment
  }

  val Empty = Resource(URI.parse(""), Mime.Unknown, 0)

  def resourceUri(path: String) = URI.parse(s"content://$authority$path")

  lazy val resolver = shadowOf_(getShadowApplication.getContentResolver).asInstanceOf[ShadowContentResolver2]

  val resources = new scala.collection.mutable.HashMap[URI, Resource]

  def getResource(uri: URI) = resources.getOrElseUpdate(uri, {
    Try {
      val len = IoUtils.toByteArray(getClass.getResourceAsStream(uri.getPath)).length
      Resource(uri, Mime.fromFileName(uri.getLastPathSegment), len)
    } getOrElse Empty
  })

  override def getType(uri: Uri): String = getResource(new AndroidURI(uri)).mime.str

  override def query(uri: Uri, projection: Array[String], selection: String, selectionArgs: Array[String], sortOrder: String): Cursor =
    getResource(new AndroidURI(uri)) match {
      case `Empty` => null
      case res => cursor(Vector(DISPLAY_NAME, SIZE), Vector(Vector(res.name, res.size.toString)))
    }
}
