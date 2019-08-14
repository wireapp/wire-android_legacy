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
package com.waz.model

import com.waz.api
import com.waz.log.LogShow.SafeToLog
import com.waz.utils.{EnumCodec, JsonDecoder, JsonEncoder}
import org.json.{JSONException, JSONObject}

sealed abstract class AssetStatus(val status: api.AssetStatus) extends SafeToLog {
  override def hashCode(): Int = status.hashCode()

  override def equals(obj: scala.Any): Boolean = obj match {
    case as: AssetStatus => status == as.status
    case _ => false
  }
}

object AssetStatus {
  import JsonDecoder._
  import api.AssetStatus._

  sealed trait Sync
  type Syncable = AssetStatus with Sync

  case object UploadNotStarted extends AssetStatus(UPLOAD_NOT_STARTED)
  case object UploadInProgress extends AssetStatus(UPLOAD_IN_PROGRESS)
  case object UploadDone extends AssetStatus(UPLOAD_DONE)
  case object UploadCancelled extends AssetStatus(UPLOAD_CANCELLED) with Sync
  case object UploadFailed extends AssetStatus(UPLOAD_FAILED) with Sync
  case object DownloadFailed extends AssetStatus(DOWNLOAD_FAILED)

  implicit lazy val Order: Ordering[AssetStatus] = Ordering.by(_.status)

  def unapply(st: AssetStatus): Option[api.AssetStatus] = Some(st.status)

  implicit lazy val AssetStatusDecoder: JsonDecoder[AssetStatus] = new JsonDecoder[AssetStatus] {
    override def apply(implicit js: JSONObject): AssetStatus = AssetStatusCodec.decode('status) match {
      case UPLOAD_NOT_STARTED   => UploadNotStarted
      case UPLOAD_IN_PROGRESS   => UploadInProgress
      case UPLOAD_DONE          => UploadDone
      case UPLOAD_CANCELLED     => UploadCancelled
      case UPLOAD_FAILED        => UploadFailed
      case DOWNLOAD_FAILED      => DownloadFailed
      case DOWNLOAD_DONE        => UploadDone // this will never be used in AssetData
      case DOWNLOAD_IN_PROGRESS => UploadDone // this will never be used in AssetData
    }
  }

  implicit lazy val AssetStatusEncoder: JsonEncoder[AssetStatus] = new JsonEncoder[AssetStatus] {
    override def apply(data: AssetStatus): JSONObject = JsonEncoder { o =>
      o.put("status", AssetStatusCodec.encode(data.status))
    }
  }

  implicit lazy val SyncableAssetStatusDecoder: JsonDecoder[Syncable] = AssetStatusDecoder.map {
    case a: AssetStatus.Syncable => a
    case other => throw new JSONException(s"not a syncable asset status: $other")
  }

  implicit lazy val SyncableAssetStatusEncoder: JsonEncoder[Syncable] = AssetStatusEncoder.comap(identity)

  implicit lazy val AssetStatusCodec: EnumCodec[api.AssetStatus, String] = EnumCodec.injective {
    case UPLOAD_NOT_STARTED   => "NotStarted"
    case UPLOAD_IN_PROGRESS   => "InProgress"
    case UPLOAD_DONE          => "Done"
    case UPLOAD_CANCELLED     => "Cancelled"
    case UPLOAD_FAILED        => "Failed"
    case DOWNLOAD_IN_PROGRESS => "DownloadInProgress"
    case DOWNLOAD_DONE        => "DownloadDone"
    case DOWNLOAD_FAILED      => "DownloadFailed"
  }
}
