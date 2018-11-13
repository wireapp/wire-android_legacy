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

import android.content.Context
import android.graphics.Bitmap.CompressFormat
import android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
import com.waz.model.Mime
import com.waz.model.errors.{NotFoundLocal, NotSupportedError}
import com.waz.service.assets2.Asset.{General, Video}
import com.waz.service.assets2.{AssetPreviewService, CanExtractMetadata, Content, RawAsset}

import scala.concurrent.{ExecutionContext, Future}

class AssetPreviewServiceImpl(implicit context: Context, ec: ExecutionContext) extends AssetPreviewService {
  import MetadataExtractionUtils._

  override def extractPreview(rawAsset: RawAsset[General], content: CanExtractMetadata): Future[Content] = {
    rawAsset.details match {
      case _: Video => extractVideoPreview(rawAsset, content)
      case _ => Future.failed(NotSupportedError(s"Preview extraction for $rawAsset not supported"))
    }
  }

  def extractVideoPreview(rawAsset: RawAsset[General], content: CanExtractMetadata): Future[Content] = {
    Future(asSource(content)).flatMap { source =>
      createMetadataRetriever(source).acquire { retriever =>
        Option(retriever.getFrameAtTime(-1L, OPTION_CLOSEST_SYNC)) match {
          case None =>
            Future.failed(NotFoundLocal(s"Can not extract video preview for $rawAsset"))
          case Some(frame) =>
            val compressedFrame = ImageCompressUtils.compress(frame, CompressFormat.JPEG)
            val content = Content.Bytes(Mime.Image.Jpg, compressedFrame)
            Future.successful(content)
        }
      }
    }
  }

}
