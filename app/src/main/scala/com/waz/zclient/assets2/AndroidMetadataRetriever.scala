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
package com.waz.zclient.assets2

import java.io.File
import java.net.URI

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.waz.utils.{Cleanup, Managed}

import scala.concurrent.{ExecutionContext, Future}

class AndroidMetadataRetriever(context: Context) {

  implicit lazy val RetrieverCleanup: Cleanup[MediaMetadataRetriever] =
    new Cleanup[MediaMetadataRetriever] {
      override def apply(a: MediaMetadataRetriever): Unit = a.release()
    }

  private def apply[A](body: MediaMetadataRetriever => A)(implicit ec: ExecutionContext): Future[A] =
    Future { Managed(new MediaMetadataRetriever).acquire { body } }

  def apply[A](file: File)(f: MediaMetadataRetriever => A)(implicit ec: ExecutionContext): Future[A] =
    apply { retriever =>
      retriever.setDataSource(file.getAbsolutePath)
      f(retriever)
    }

  def apply[A](uri: URI)(f: MediaMetadataRetriever => A)(implicit ec: ExecutionContext): Future[A] =
    apply { retriever =>
      val androidUri = Uri.parse(uri.toString)
      retriever.setDataSource(context, androidUri)
      f(retriever)
    }
}

object AndroidMetadataRetriever {



}
