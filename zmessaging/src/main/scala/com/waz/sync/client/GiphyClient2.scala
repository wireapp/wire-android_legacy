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

import java.net.{URL, URLEncoder}

import com.waz.ZLog.ImplicitTag._
import com.waz.model.Dim2
import com.waz.sync.client.GiphyClient2.{Gif, GifObject}
import com.waz.threading.CancellableFuture
import com.waz.utils.CirceJSONSupport
import com.waz.znet2.AuthRequestInterceptor2
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request}

trait GiphyClient2 {
  def loadTrending(offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[GifObject]]
  def search(keyword: String, offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[GifObject]]
}

class GiphyClientImpl2(implicit
                       urlCreator: UrlCreator,
                       httpClient: HttpClient,
                       authRequestInterceptor: AuthRequestInterceptor2) extends GiphyClient2 with CirceJSONSupport {

  import GiphyClient2.GiphyResponse
  import HttpClient.AutoDerivation._
  import HttpClient.dsl._
  import com.waz.threading.Threading.Implicits.Background

  private val BasePath = "/proxy/giphy/v1/gifs"

  override def loadTrending(offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[GifObject]] = {
    Request
      .Get(
        relativePath = s"$BasePath/trending",
        queryParameters("offset" -> offset, "limit" -> limit)
      )
      .withResultType[GiphyResponse]
      .execute
      .map(createGifObjects)
  }

  override def search(keyword: String, offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[GifObject]] = {
    Request
      .Get(
        relativePath = s"$BasePath/search",
        queryParameters("q" -> URLEncoder.encode(keyword, "utf8"), "offset" -> offset, "limit" -> limit)
      )
      .withResultType[GiphyResponse]
      .execute
      .map(createGifObjects)
  }

  //TODO This conversions should be done in the domain layer
  private def createGifObjects(response: GiphyResponse): Seq[GifObject] = {
    response.data.filter(_.`type` == "gif").map { obj =>
      GifObject(
        original = createGif(obj.images.original),
        preview = obj.images.fixed_width_downsampled.map(createGif)
      )
    }
  }

  private def createGif(img: GiphyResponse.Image): Gif = {
    Gif(Dim2(img.width, img.height), img.size, img.url)
  }

}

object GiphyClient2 {

  //TODO This models should live in the domain layer
  case class GifObject(original: Gif, preview: Option[Gif])
  case class Gif(dimensions: Dim2, sizeInBytes: Long, source: URL)

  /**
    * This object and all nested contains much more fields, but for now we do not need them.
    */
  case class GiphyResponse(data: Seq[GiphyResponse.ImageBundle])
  object GiphyResponse {

    /**
      * @param `type` By default, this is almost always 'gif'
      */
    case class ImageBundle(`type`: String, images: Images)
    case class Images(original: Image, fixed_width_downsampled: Option[Image])
    case class Image(url: URL, width: Int, height: Int, size: Long)
  }

}
