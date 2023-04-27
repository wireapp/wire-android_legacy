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

import com.waz.model.SyncId

import java.net.{URL, URLEncoder}
import com.waz.sync.client.GiphyClient.GiphyResponse
import com.waz.utils.CirceJSONSupport
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request}

import scala.concurrent.Future

trait GiphyClient {
  def trending(offset: Int, limit: Int): Future[GiphyResponse]
  def search(keyword: String, offset: Int, limit: Int): Future[GiphyResponse]
}

class GiphyClientImpl(implicit
                      urlCreator: UrlCreator,
                      httpClient: HttpClient,
                      authRequestInterceptor: AuthRequestInterceptor) extends GiphyClient with CirceJSONSupport {

  import GiphyClient.GiphyResponse
  import HttpClient.AutoDerivation._
  import HttpClient.dsl._

  private val BasePath = "/proxy/giphy/v1/gifs"

  override def trending(offset: Int, limit: Int): Future[GiphyResponse] = {
    Request
      .Get(
        relativePath = s"$BasePath/trending",
        queryParameters("offset" -> offset, "limit" -> limit)
      )
      .withResultType[GiphyResponse]()
      .execute
  }

  override def search(keyword: String, offset: Int, limit: Int): Future[GiphyResponse] = {
    Request
      .Get(
        relativePath = s"$BasePath/search",
        queryParameters("q" -> URLEncoder.encode(keyword, "utf8"), "offset" -> offset, "limit" -> limit)
      )
      .withResultType[GiphyResponse]()
      .execute
  }

}

object GiphyClient {

  /**
    * This object and all nested contains much more fields, but for now we do not need them.
    */
  case class GiphyResponse(data: Seq[GiphyResponse.ImageBundle])
  object GiphyResponse {

    /**
      * @param `type` By default, this is almost always 'gif'
      */
    case class ImageBundle(id: String, `type`: String, images: Images)
    case class Images(original: Image, fixed_width_downsampled: Option[Image])
    case class Image(url: URL, width: Int, height: Int, size: Long)
  }

}
