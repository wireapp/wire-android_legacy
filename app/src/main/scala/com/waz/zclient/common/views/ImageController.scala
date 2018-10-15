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
package com.waz.zclient.common.views

import com.waz.content.UserPreferences
import com.waz.model.AssetData.{IsImage, IsVideo}
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.images.BitmapSignal
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.Signal
import com.waz.zclient.{Injectable, Injector}

class ImageController(implicit inj: Injector) extends Injectable {

  val zMessaging = inject[Signal[ZMessaging]]

  def imageData(id: AssetId, zms: ZMessaging) =
    zms.assetsStorage.signal(id).flatMap {
      case a@IsImage() => Signal.const(a)
      case a@IsVideo() => a.previewId.fold(Signal.const(AssetData.Empty))(zms.assetsStorage.signal)
      case _ => Signal.const(AssetData.Empty)
    }

  def imageSignal(id: AssetId, req: BitmapRequest, forceDownload: Boolean): Signal[BitmapResult] =
    zMessaging.flatMap(imageSignal(_, id, req, forceDownload))

  def imageSignal(zms: ZMessaging, id: AssetId, req: BitmapRequest, forceDownload: Boolean = true): Signal[BitmapResult] =
    for {
      data <- imageData(id, zms)
      res <- BitmapSignal(data, req, zms.imageLoader, zms.network, zms.assetsStorage.get, zms.userPrefs.preference(UserPreferences.DownloadImagesAlways).signal, forceDownload)
    } yield res

  def imageSignal(data: AssetData, req: BitmapRequest, forceDownload: Boolean): Signal[BitmapResult] =
    zMessaging flatMap { zms => BitmapSignal(data, req, zms.imageLoader, zms.network, zms.assetsStorage.get, forceDownload = forceDownload) }
}
