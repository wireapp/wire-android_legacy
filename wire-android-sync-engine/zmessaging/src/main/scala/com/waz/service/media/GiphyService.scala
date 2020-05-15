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

import java.net.URL

import com.waz.model.Dim2
import com.waz.service.media.GiphyService.{Gif, GifObject}
import com.waz.sync.client.GiphyClient
import com.waz.sync.client.GiphyClient.GiphyResponse

import scala.concurrent.{ExecutionContext, Future}

trait GiphyService {
  def search(keyword: String, offset: Int = 0, limit: Int = 25): Future[Seq[GifObject]]
  def trending(offset: Int = 0, limit: Int = 25): Future[Seq[GifObject]]
}

object GiphyService {
  val MaxGifSize         = 5 * 1024 * 1024

  case class GifObject(id: String, original: Gif, preview: Option[Gif])
  case class Gif(dimensions: Dim2, sizeInBytes: Long, source: URL)
}

class GiphyServiceImpl(client: GiphyClient)(implicit ec: ExecutionContext) extends GiphyService {

  override def search(keyword: String, offset: Int = 0, limit: Int = 25): Future[Seq[GifObject]] =
    for {
      response <- client.search(keyword, offset, limit)
      objects = createGifObjects(response)
    } yield objects.filter(isGifValid)

  override def trending(offset: Int = 0, limit: Int = 25): Future[Seq[GifObject]] =
    for {
      response <- client.trending(offset, limit)
      objects = createGifObjects(response)
    } yield objects.filter(isGifValid)

  private def createGifObjects(response: GiphyResponse): Seq[GifObject] = {
    response.data.filter(_.`type` == "gif").map { obj =>
      GifObject(
        id = obj.id,
        original = createGif(obj.images.original),
        preview = obj.images.fixed_width_downsampled.map(createGif)
      )
    }
  }

  private def createGif(img: GiphyResponse.Image): Gif = {
    Gif(Dim2(img.width, img.height), img.size, img.url)
  }

  private def isGifValid(gifObject: GifObject): Boolean =
    gifObject.original.sizeInBytes <= GiphyService.MaxGifSize

}