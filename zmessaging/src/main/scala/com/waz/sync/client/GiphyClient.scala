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

import java.net.URLEncoder

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.{AssetData, AssetMetaData, Dim2, Mime}
import com.waz.threading.CancellableFuture
import com.waz.utils.wrappers.URI
import com.waz.utils.{JsonDecoder, LoggedTry}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import org.json.JSONObject

import scala.util.Try
import scala.util.control.NonFatal

trait GiphyClient {
  def loadTrending(offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[(Option[AssetData], AssetData)]]
  def search(keyword: String, offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[(Option[AssetData], AssetData)]]
}

class GiphyClientImpl(implicit
                      urlCreator: UrlCreator,
                      httpClient: HttpClient,
                      authRequestInterceptor: AuthRequestInterceptor) extends GiphyClient {

  import GiphyClient._
  import HttpClient.dsl._
  import HttpClient.AutoDerivation._
  import com.waz.threading.Threading.Implicits.Background

  private implicit val giphySeqDeserializer: RawBodyDeserializer[Seq[(Option[AssetData], AssetData)]] =
    RawBodyDeserializer[JSONObject].map(json => GiphyResponse.unapply(JsonObjectResponse(json)).get)

  override def loadTrending(offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[(Option[AssetData], AssetData)]] = {
    Request.Get(relativePath = trendingPath(offset, limit))
      .withResultType[Seq[(Option[AssetData], AssetData)]]
      .execute
      .recover { case err =>
        warn(s"unexpected response for trending: $err")
        Nil
      }
  }

  override def search(keyword: String, offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[(Option[AssetData], AssetData)]] = {
    Request.Get(relativePath = searchPath(keyword, offset, limit))
      .withResultType[Seq[(Option[AssetData], AssetData)]]
      .execute
      .recover { case err =>
        warn(s"unexpected response for search keyword '$keyword': $err")
        Nil
      }
  }

}

object GiphyClient {
  val BasePath = "/proxy/giphy/v1/gifs"

  def searchPath(keyword: String, offset: Int, limit: Int) = s"$BasePath/search?q=${URLEncoder.encode(keyword, "utf8")}&offset=$offset&limit=$limit"

  def trendingPath(offset: Int, limit: Int) = s"$BasePath/trending?offset=$offset&limit=$limit"

  implicit lazy val GiphyAssetOrdering: Ordering[AssetData] = new Ordering[AssetData] {
    override def compare(x: AssetData, y: AssetData): Int = {
      if (x.dimensions.width == y.dimensions.width) {
        if (x.tag == Preview || y.tag == Medium) -1
        else if (x.tag == Medium || y.tag == Preview) 1
        else Ordering.Int.compare(x.size.toInt, y.size.toInt)
      } else Ordering.Int.compare(x.dimensions.width, y.dimensions.width)
    }
  }

  object GiphyResponse {

    object Decoder extends JsonDecoder[Option[AssetData]] {
      import JsonDecoder._

      override def apply(implicit js: JSONObject): Option[AssetData] = {
        val uri = decodeOptString('url).map(URI.parse)
        uri map (uri => AssetData(mime = Mime.Image.Gif, metaData = Some(AssetMetaData.Image(Dim2('width, 'height))), sizeInBytes = 'size, source = Some(uri)))
      }
    }

    def unapply(response: ResponseContent): Option[Seq[(Option[AssetData], AssetData)]] = try {
      response match {
        case JsonObjectResponse(js) => decode(js)
        case StringResponse(json) => Try(new JSONObject(json)).toOption.flatMap(decode)
        case _ => None
      }
    } catch {
      case NonFatal(e) =>
        warn(s"response: $response parsing failed", e)
        None
    }

    def parseImage(tag: Tag, data: JSONObject): Option[AssetData] = Decoder(data).map { a =>
      a.copy(metaData = Some(AssetMetaData.Image(a.dimensions, tag)))
    }

    def decode(js: JSONObject) = {
      js.getJSONObject("meta").getInt("status") match {
        case 200 => LoggedTry {
          JsonDecoder.array(js.getJSONArray("data"), { (arr, index) =>
            val images = arr.getJSONObject(index).getJSONObject("images")

            val assets = Seq("fixed_width_downsampled", "original").flatMap { key =>
              parseImage(if (key == "fixed_width_downsampled") Preview else Medium, images.getJSONObject(key))
            }

            val preview = assets.headOption
            (preview, assets.lastOption.map(_.copy(previewId = preview.map(_.id))).getOrElse(AssetData.Empty))
          })
        } .toOption
        case _ => None
      }
    }
  }
}
