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
package com.waz.zclient.glide

import java.io.InputStream

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.errors.NotSupportedError
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetInput
import com.wire.signals.CancellableFuture
import com.wire.signals.Signal

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

final class ImageAssetFetcher(request: AssetRequest, zms: Signal[ZMessaging])
  extends DataFetcher[InputStream] with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  @volatile
  private var currentData: Option[CancellableFuture[AssetInput]] = None

  override def loadData(priority: Priority, callback: DataFetcher.DataCallback[_ >: InputStream]): Unit = {
    val data = CancellableFuture.lift(zms.head).flatMap { zms =>
      request match {
        case AssetIdRequest(assetId)             => zms.assetService.loadContentById(assetId)
        case PublicAssetIdRequest(assetId)       => zms.assetService.loadPublicContentById(assetId, None)
        case UploadAssetIdRequest(uploadAssetId) => zms.assetService.loadUploadContentById(uploadAssetId, None)
        case MapRequest(location)                => zms.mapsMediaService.loadMapPreview(location)
        case _                                   => CancellableFuture.failed(NotSupportedError("Unsupported image request"))
      }
    }

    data.addTimeout(30.seconds)

    currentData.foreach(_.cancel())
    currentData = Some(data)

    data.future.flatMap(input => Future.fromTry(input.toInputStream)).onComplete {
      case Failure(err) =>
        callback.onLoadFailed(new RuntimeException(s"Fetcher. Asset loading failed: $err"))
      case Success(is) =>
        callback.onDataReady(is)
    }
  }

  override def cleanup(): Unit = ()

  override def cancel(): Unit = {
    currentData.foreach(_.cancel())
    currentData = None
  }

  override def getDataClass: Class[InputStream] = classOf[InputStream]

  override def getDataSource: DataSource = DataSource.REMOTE
}
