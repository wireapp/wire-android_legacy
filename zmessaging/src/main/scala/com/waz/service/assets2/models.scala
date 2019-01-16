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
package com.waz.service.assets2

import java.net.URI

import com.waz.cache2.CacheService.Encryption
import com.waz.model._
import com.waz.sync.client.AssetClient2.Retention
import com.waz.utils.Identifiable
import org.threeten.bp.Duration

//TODO Maybe we can remove encryption here
case class RawAsset[+T <: AssetDetails](
    override val id: AssetId,
    uri: URI,
    sha: Sha256,
    mime: Mime,
    size: Long,
    retention: Retention,
    public: Boolean,
    encryption: Encryption,
    details: T,
    @deprecated convId: Option[RConvId]
) extends Identifiable[AssetId]

case class Asset[+T <: AssetDetails](
    override val id: AssetId,
    token: Option[AssetToken], //all not public assets should have an AssetToken
    sha: Sha256,
    encryption: Encryption,
    localSource: Option[URI],
    preview: Option[AssetId],
    details: T,
    @deprecated convId: Option[RConvId]
) extends Identifiable[AssetId]

object Asset {
  type General = AssetDetails
  type Blob    = BlobDetails.type
  type Image   = ImageDetails
  type Audio   = AudioDetails
  type Video   = VideoDetails

  def apply(assetId: AssetId, token: Option[AssetToken], rawAsset: RawAsset[General]): Asset[General] =
    Asset(
      id = assetId,
      token = token,
      sha = rawAsset.sha,
      encryption = rawAsset.encryption,
      localSource = None,
      preview = None,
      details = rawAsset.details,
      convId = rawAsset.convId
    )

}

sealed trait AssetDetails
case object BlobDetails                                                                 extends AssetDetails
case class ImageDetails(dimensions: Dim2, tag: ImageTag)                                extends AssetDetails
case class AudioDetails(duration: Duration, loudness: Loudness)                         extends AssetDetails
case class VideoDetails(dimensions: Dim2, duration: Duration) extends AssetDetails

sealed trait ImageTag
case object Preview extends ImageTag
case object Medium  extends ImageTag
case object Empty   extends ImageTag

case class Loudness(levels: Vector[Float])
