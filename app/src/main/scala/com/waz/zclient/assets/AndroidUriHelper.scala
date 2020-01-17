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

import java.io.{File, InputStream}
import java.net.URI

import android.content.{ContentResolver, Context}
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.Mime
import com.waz.service.assets.UriHelper
import com.waz.utils.Managed
import com.waz.zclient.log.LogUI._

import scala.util.Try

class AndroidUriHelper(context: Context) extends UriHelper with DerivedLogTag {

  private def androidUri(uri: URI): Uri = Uri.parse(uri.toString)

  override def openInputStream(uri: URI): Try[InputStream] = Try {
    context.getContentResolver.openInputStream(androidUri(uri))
  }

  override def extractMime(uri: URI): Try[Mime] = Try {
    if (uri.getScheme == ContentResolver.SCHEME_FILE) {
      Mime.fromFileName(uri.getPath)
    } else {
      val mimeStr = Option(context.getContentResolver.getType(androidUri(uri))).get
      Mime(mimeStr)
    }
  }

  override def extractSize(uri: URI): Try[Long] = Try {
    debug(l"Extracting size for $uri")

    if (uri.getScheme == ContentResolver.SCHEME_FILE) {
      val file = new File(uri.getPath)
      file.length()
    } else {
      cursor(uri).acquire { c =>
        c.moveToFirst()
        c.getLong(c.getColumnIndex(OpenableColumns.SIZE))
      }
    }
  }

  override def extractFileName(uri: URI): Try[String] = Try {
    debug(l"Extracting file name for $uri")

    if (uri.getScheme == ContentResolver.SCHEME_FILE) {
      val file = new File(uri.getPath)
      file.getName
    } else {
      cursor(uri).acquire { c =>
        c.moveToFirst()
        c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME))
      }
    }
  }

  private def cursor(uri: URI): Managed[Cursor] =
    Managed.create(
      context.getContentResolver.query(androidUri(uri), null, null, null, null)
    )(_.close())

}
