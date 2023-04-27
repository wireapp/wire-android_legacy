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
package com.waz.sync.client

import java.io.InputStream
import java.net.URLEncoder
import com.waz.api.impl.ErrorResponse
import com.waz.model.{Dim2, SyncId}
import com.waz.service.media.RichMediaContentParser.MapsLocation
import com.waz.utils.CirceJSONSupport
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.HttpClient.AutoDerivation._
import com.waz.znet2.http.HttpClient.dsl._
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._

trait MapsClient {
  def loadMapPreview(location: MapsLocation, dimensions: Dim2): ErrorOrResponse[InputStream]
}

class MapsClientImpl(implicit
                     urlCreator: UrlCreator,
                     httpClient: HttpClient,
                     authRequestInterceptor: AuthRequestInterceptor) extends MapsClient with CirceJSONSupport {

  import MapsClient._

  private implicit def inputStreamBodyDeserializer: RawBodyDeserializer[InputStream] = RawBodyDeserializer.create(_.data())

  def loadMapPreview(location: MapsLocation, dimensions: Dim2): ErrorOrResponse[InputStream] =
    Request
      .Get(relativePath = getStaticMapPath(location, dimensions.width, dimensions.height))
      .withResultType[InputStream]()
      .withErrorType[ErrorResponse]
      .executeSafe

}

object MapsClient {

  // FIXME: use openstreetmap
  val StaticMapsPathBase = "/proxy/googlemaps/api/staticmap"

  def getStaticMapPath(location: MapsLocation, width: Int, height: Int): String = {
    val center = URLEncoder.encode(s"${location.latitude},${location.longitude}", "utf8")
    val zoom = URLEncoder.encode(location.zoom, "utf8")
    s"$StaticMapsPathBase?center=$center&zoom=$zoom&size=${width}x$height"
  }
}
