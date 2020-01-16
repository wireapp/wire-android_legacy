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

import com.waz.model.{AssetId, GeneralAssetId, Picture, PictureNotUploaded, PictureUploaded, UploadAssetId}
import com.waz.api.MessageContent.Location
import com.waz.service.assets.Asset

sealed trait AssetRequest {
  val key: String
}

object AssetRequest {
  def apply(assetIdGeneral: GeneralAssetId): AssetRequest = {
    assetIdGeneral match {
      case a: UploadAssetId => UploadAssetIdRequest(a)
      case a: AssetId => AssetIdRequest(a)
      case _ => EmptyRequest()
    }
  }
  def apply(picture: Picture): AssetRequest = picture match {
    case PictureUploaded(assetId) => PublicAssetIdRequest(assetId)
    case PictureNotUploaded(assetId) => UploadAssetIdRequest(assetId)
  }
}

case class AssetIdRequest(assetId: AssetId) extends AssetRequest {
  override val key: String = assetId.str
}
case class PublicAssetIdRequest(assetId: AssetId) extends AssetRequest {
  override val key: String = assetId.str
}
case class ImageAssetRequest(asset: Asset) extends AssetRequest {
  override val key: String = asset.id.str
}
case class UploadAssetIdRequest(assetId: UploadAssetId) extends AssetRequest {
  override val key: String = assetId.str
}
case class GoogleMapRequest(location: Location) extends AssetRequest {
  override val key: String = location.toString
}
case class EmptyRequest() extends AssetRequest {
  override val key: String = ""
}
