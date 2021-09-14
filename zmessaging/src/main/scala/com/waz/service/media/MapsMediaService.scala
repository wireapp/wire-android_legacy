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
package com.waz.service.media

import com.waz.api.MessageContent.Location
import com.waz.model._
import com.waz.service.assets.AssetInput
import com.waz.service.media.RichMediaContentParser.MapsLocation
import com.waz.sync.client.MapsClient
import com.wire.signals.CancellableFuture

import scala.concurrent.ExecutionContext

trait MapsMediaService {
  def loadMapPreview(location: Location, dimensions: Dim2): CancellableFuture[AssetInput]
}

class MapsMediaServiceImpl(mapsClient: MapsClient)(implicit executionContext: ExecutionContext)
  extends MapsMediaService {

  import com.waz.service.media.MapsMediaService._

  def loadMapPreview(location: Location, dimensions: Dim2 = ImageDimensions): CancellableFuture[AssetInput] = {
    val mapsLocation = MapsLocation(location.getLatitude.toString, location.getLongitude.toString, location.getZoom.toString)
    val constrainedDimensions = constrain(dimensions)

    mapsClient.loadMapPreview(mapsLocation, constrainedDimensions).flatMap {
      case Left(error) => CancellableFuture.failed(error)
      case Right(result) => CancellableFuture.successful(result)
    }.map(AssetInput(_))
  }
}

object MapsMediaService {

  val MaxImageWidth = 640 // free maps api limitation
  val ImageDimensions = Dim2(MaxImageWidth, MaxImageWidth * 3 / 4)
  val PreviewWidth = 64

  def constrain(dimensions: Dim2): Dim2 = {
    val mapWidth = math.min(MaxImageWidth, dimensions.width)
    val mapHeight = mapWidth * dimensions.height / dimensions.width
    Dim2(mapWidth, mapHeight)
  }
}
