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
package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse._
import com.waz.cache.CacheService
import com.waz.model.AssetStatus.{UploadCancelled, UploadFailed, UploadInProgress, UploadNotStarted}
import com.waz.model._
import com.waz.service.assets.AssetService
import com.waz.sync.client.AssetClient.Retention
import com.waz.sync.otr.OtrSyncHandler
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.sync.client.ErrorOrResponse

class AssetSyncHandler(teamId:  Option[TeamId],
                       cache:   CacheService,
                       assets:  AssetService,
                       otrSync: OtrSyncHandler) {

  import Threading.Implicits.Background

  def uploadAssetData(assetId: AssetId, public: Boolean = false, retention: Retention): ErrorOrResponse[AssetData] =
    CancellableFuture.lift(assets.updateAsset(assetId, asset => asset.copy(status = if (asset.status == UploadNotStarted) UploadInProgress else asset.status )).zip(assets.getLocalData(assetId))) flatMap {
      case (Some(asset), Some(data)) if data.length > AssetData.maxAssetSizeInBytes(teamId.isDefined) =>
//        debug(s"Local data too big. Data length: ${data.length}, max size: ${AssetData.maxAssetSizeInBytes(teamId.isDefined)}, local data: $data, asset: $asset")
        CancellableFuture successful Left(internalError(AssetSyncHandler.AssetTooLarge))

      case (Some(asset), _) if asset.remoteId.isDefined =>
//        warn(s"asset has already been uploaded, skipping: $asset")
        CancellableFuture.successful(Left(internalError("asset has already been uploaded, skipping")))

      case (Some(asset), Some(data)) if Set[AssetStatus](UploadInProgress, UploadFailed).contains(asset.status) =>
        otrSync.uploadAssetDataV3(data, if (public) None else Some(AESKey()), asset.mime, retention).flatMap {
          case Right(remoteData) => CancellableFuture.lift(assets.updateAsset(asset.id, _.copyWithRemoteData(remoteData)).map {
            case Some(updated) => Right(updated)
            case None          => Left(internalError("asset update failed"))
          })
          case Left(err) => CancellableFuture successful Left(err)
        }

      case (Some(asset), Some(_)) if asset.status == UploadCancelled =>
//        debug(s"Upload for asset was cancelled")
        CancellableFuture successful Left(internalError("Upload for asset was cancelled"))

      case (asset, local) =>
//        debug(s"Unable to handle asset upload with asset: $asset, and local data: $local")
        CancellableFuture successful Left(internalError(s"Unable to handle asset upload with asset: $asset, and local data: $local"))
    }
}

object AssetSyncHandler {
  val AssetTooLarge = "Failed to upload asset: Asset is too large"
}