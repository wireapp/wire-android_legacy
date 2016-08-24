/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.controllers

import com.waz.model.{AnyAssetData, AssetId, AssetPreviewData, ImageAssetData}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapRequest.Regular
import com.waz.service.assets.AssetService.{BitmapRequest, BitmapResult}
import com.waz.service.images.BitmapSignal
import com.waz.utils.events.Signal
import com.waz.zclient.{Injectable, Injector}

class ImageController(implicit inj: Injector) extends Injectable {

  val zMessaging = inject[Signal[ZMessaging]]

  def imageData(id: AssetId) = zMessaging.flatMap { zms =>
    zms.assetsStorage.signal(id) map {
      case im: ImageAssetData => im
      case AnyAssetData(_, conv, _, _, _, _, Some(AssetPreviewData.Image(preview)), _, _, _, _) => ImageAssetData(id, conv, Seq(preview))
      case _ => ImageAssetData.Empty
    }
  }

  def imageSignal(id: AssetId, width: Int): Signal[BitmapResult] =
    imageSignal(id, Regular(width))

  def imageSignal(id: AssetId, req: BitmapRequest): Signal[BitmapResult] =
    for {
      zms <- zMessaging
      data <- imageData(id)
      res <- BitmapSignal(data, req, zms.imageLoader, zms.imageCache)
    } yield res
}
