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
package com.waz.content

import android.content.{ContentProvider, ContentResolver, ContentValues, Context}
import android.database.{Cursor, MatrixCursor}
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.waz.log.LogSE._
import com.waz.cache.{CacheEntryData, Expiration}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.CacheKey
import com.waz.service.ZMessaging
import com.waz.threading.CancellableFuture
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.returning
import com.waz.utils.wrappers.{AndroidURI, URI}

import scala.concurrent.Await
import scala.concurrent.duration._

class WireContentProvider extends ContentProvider with DerivedLogTag {
  import WireContentProvider._

  private val AsyncTimeout = 15.seconds

  private def cache = ZMessaging.currentGlobal.cache

  private def getEntry(key: CacheKey) = Await.result(cache.getEntry(key), AsyncTimeout)

  override def getType(uri: Uri): String = {
    verbose(l"getType($uri)")
    uri match {
      case CacheUriExtractor(key) =>
        returning(getEntry(key).map(_.data.mimeType.str).orNull) { tpe => verbose(l"found entry type: ${showString(tpe)}")}
      case _ =>
        verbose(l"content not found")
        null
    }
  }

  override def update(uri: Uri, values: ContentValues, selection: String, selectionArgs: Array[String]): Int = 0

  override def insert(uri: Uri, values: ContentValues): Uri = null

  override def delete(uri: Uri, selection: String, selectionArgs: Array[String]): Int = 0

  override def onCreate(): Boolean = {
    verbose(l"onCreate")
    true
  }

  override def query(uri: Uri, projection: Array[String], selection: String, selectionArgs: Array[String], sortOrder: String): Cursor = {
    verbose(l"query($uri)")
    val columns = Option(projection).getOrElse(Array(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
    val result = new MatrixCursor(columns)

    uri match {
      case CacheUriExtractor(key) =>
        getEntry(key) foreach { entry =>
          verbose(l"found entry: $entry")
          result.addRow(columns map {
            case OpenableColumns.DISPLAY_NAME => entry.data.fileName.getOrElse("")
            case OpenableColumns.SIZE => java.lang.Long.valueOf(entry.length)
            case _ => null.asInstanceOf[AnyRef]
          })
        }
      case _ => // ignore
    }

    result
  }

  override def openFile(uri: Uri, mode: String): ParcelFileDescriptor = {
    verbose(l"openFile($uri, ${showString(mode)}")
    uri match {
      case CacheUriExtractor(key) =>
        verbose(l"CacheUriExtractor: $key")
        Await.result(getDecryptedEntry(key), AsyncTimeout) match {
          case Some(entry) =>
            val file = entry.copyDataToFile()
            verbose(l"found entry, copying data to file: $file. Data contains: ${entry.length} bytes")
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
          case None =>
            verbose(l"no cache entry found, attempting to open uri: $uri")
            super.openFile(uri, mode)
        }
      case _ =>
        super.openFile(uri, mode)
    }
  }

  private def getDecryptedEntry(key: CacheKey) =
    cache.getEntry(key) flatMap {
      case Some(entry) if entry.data.encKey.isDefined =>
        verbose(l"getDecryptedEntry: entry was decrypted")
        cache.getOrElse(CacheKey.decrypted(key), cache.addStream(key, entry.inputStream, entry.data.mimeType, entry.data.fileName, Some(cache.intCacheDir))(Expiration.in(12.hours))) map (Some(_))
      case res =>
        verbose(l"getDecryptedEntry: entry was NOT decrypted")
        CancellableFuture successful res
    }

  object CacheUriExtractor {
    val extractor = CacheUri.unapply(getContext) _

    def unapply(uri: Uri): Option[CacheKey] = extractor(new AndroidURI(uri))
  }
}

object WireContentProvider {
  private val Cache = "cache"

  object CacheUri {

    private def builder(context: Context) = URI.parse(ContentResolver.SCHEME_CONTENT + "://" + context.getPackageName).buildUpon

    def apply(key: CacheKey, context: Context): URI = builder(context).appendEncodedPath(Cache).appendPath(key.str).build

    def apply(entry: CacheEntryData, context: Context): URI = {
      val b = builder(context).appendEncodedPath(Cache).appendPath(entry.key.str)
      entry.fileName.foreach(b.appendPath)
      b.build
    }

    def unapply(context: Context)(uri: URI): Option[CacheKey] =
      if (uri.getScheme != ContentResolver.SCHEME_CONTENT || uri.getAuthority != context.getPackageName) None
      else {
        val path = uri.getPathSegments
        if (path.size >= 2 && path.head == "cache") Some(CacheKey(path(1))) else None
      }
  }
}
