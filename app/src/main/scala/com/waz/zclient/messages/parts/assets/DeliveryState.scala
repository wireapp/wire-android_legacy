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
package com.waz.zclient.messages.parts.assets

import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.model._
import com.waz.utils.events.Signal
import com.waz.log.LogSE._
import com.waz.service.assets
import com.waz.service.assets.{DownloadAssetStatus, UploadAssetStatus}

sealed trait DeliveryState extends SafeToLog

object DeliveryState extends DerivedLogTag {

  case object Complete extends DeliveryState

  case object OtherUploading extends DeliveryState

  case object Uploading extends DeliveryState

  case object Downloading extends DeliveryState

  case object Cancelled extends DeliveryState

  sealed trait Failed extends DeliveryState

  case object UploadFailed extends Failed

  case object DownloadFailed extends Failed

  case object Unknown extends DeliveryState

  private def apply(as: assets.AssetStatus, ms: Message.Status): DeliveryState = {
    val res = (as, ms) match {
      case (UploadAssetStatus.Cancelled, _) => Cancelled
      case (UploadAssetStatus.Failed, _) => UploadFailed
      case (DownloadAssetStatus.Failed, _) => DownloadFailed
      case (UploadAssetStatus.NotStarted | UploadAssetStatus.InProgress, mState) =>
        mState match {
          case Message.Status.FAILED => UploadFailed
          case Message.Status.SENT => OtherUploading
          case _ => Uploading
        }
      case (DownloadAssetStatus.InProgress, _) => Downloading
      case (assets.AssetStatus.Done, _) => Complete
      case _ => Unknown
    }
    verbose(l"Mapping Asset.Status: $as, and Message.Status $ms to DeliveryState: $res")
    res
  }

  def apply(message: Signal[MessageData], asset: Signal[assets.AssetStatus]): Signal[DeliveryState] =
    message.zip(asset).map { case (m, s) => apply(s, m.state) }
}
