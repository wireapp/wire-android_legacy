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

import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.{AssetData, Dim2, Mime}
import com.waz.sync.client.GiphyClient.GiphyResponse
import com.waz.testutils.Matchers._
import com.waz.threading.CancellableFuture
import com.waz.utils.wrappers.URI
import com.waz.znet.ZNetClient.EmptyClient
import com.waz.znet.{JsonObjectResponse, Request, Response, StringResponse}
import org.json.JSONObject
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

@Ignore class GiphyClientSpec extends FeatureSpec with Matchers with RobolectricTests {

  val randomSeqResponse = {
    val fullResp = Seq(
      AssetData(metaData = Some(Image(Dim2(500, 256), Medium)), mime = Mime("image/gif"), source = Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/giphy.gif"))),
      AssetData(metaData = Some(Image(Dim2(391, 200), Medium)), mime = Mime("image/gif"), source = Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/200_d.gif"))),
      AssetData(metaData = Some(Image(Dim2(200, 102), Medium)), mime = Mime("image/gif"), source = Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/200w_d.gif"))),
      AssetData(metaData = Some(Image(Dim2(195, 100), Preview)), mime = Mime("image/gif"), source = Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/100_s.gif"))),
      AssetData(metaData = Some(Image(Dim2(100, 51), Preview)), mime = Mime("image/gif"), source = Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/100w_s.gif")))
    )
    (Some(fullResp.last), fullResp.head)
  }

  val sampleGifRespone = {
    val fullResponse = Seq(
      AssetData(metaData = Some(Image(Dim2(358, 200), Medium)), mime = Mime("image/gif"), source = Some(URI.parse("http://media3.giphy.com/media/wWAIKcFASEFz2/200.gif"))),
      AssetData(metaData = Some(Image(Dim2(358, 200), Preview)), mime = Mime("image/gif"), source = Some(URI.parse("http://media2.giphy.com/media/wWAIKcFASEFz2/200_s.gif")))
    )
    (Some(fullResponse.last), fullResponse.head)
  }
  val searchResult = Seq(sampleGifRespone, sampleGifRespone)

  feature("parsing") {

    scenario("search response") {
      new StringResponse(SearchResponse) should beMatching {
        case GiphyResponse(resp) if resp == searchResult => true
      }
    }
  }

  feature("request loading") {

    scenario("search") {
      val successfulClient = mockedClient(Response(Response.HttpStatus(200), StringResponse(SearchResponse)), Response(Response.HttpStatus(200), JsonObjectResponse(new JSONObject(SearchResponse))))
      val failedClient = mockedClient(notFound)

      successfulClient.search("superman") should eventually(be(searchResult))
      successfulClient.search("superman") should eventually(be(searchResult))

      failedClient.search("superman") should eventually(be(Nil))
    }
  }

  val RandomResponse =
    """{
      |    "data": {
      |        type: "gif",
      |        id: "Ggjwvmqktuvf2",
      |        url: "http://giphy.com/gifs/american-psycho-christian-bale-Ggjwvmqktuvf2",
      |        image_original_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/giphy.gif",
      |        image_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/giphy.gif",
      |        image_mp4_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/giphy.mp4",
      |        image_frames: "11",
      |        image_width: "500",
      |        image_height: "256",
      |        fixed_height_downsampled_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/200_d.gif",
      |        fixed_height_downsampled_width: "391",
      |        fixed_height_downsampled_height: "200",
      |        fixed_width_downsampled_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/200w_d.gif",
      |        fixed_width_downsampled_width: "200",
      |        fixed_width_downsampled_height: "102",
      |        fixed_height_small_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/100.gif",
      |        fixed_height_small_still_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/100_s.gif",
      |        fixed_height_small_width: "195",
      |        fixed_height_small_height: "100",
      |        fixed_width_small_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/100w.gif",
      |        fixed_width_small_still_url: "http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/100w_s.gif",
      |        fixed_width_small_width: "100",
      |        fixed_width_small_height: "51"
      |    },
      |    "meta": {
      |        "status": 200,
      |        "msg": "OK"
      |    }
      |}""".stripMargin

  val SampleGifResponse =
    """{
      |        type: "gif",
      |        id: "wWAIKcFASEFz2",
      |        url: "http://giphy.com/gifs/superman-santa-chandler-bing-wWAIKcFASEFz2",
      |        bitly_gif_url: "http://gph.is/XMD6gE",
      |        bitly_url: "http://gph.is/XMD6gE",
      |        embed_url: "http://giphy.com/embed/wWAIKcFASEFz2",
      |        username: "",
      |        source: "http://daytripperrevolution.tumblr.com/post/13729531842",
      |        rating: "g",
      |        caption: "",
      |        content_url: "",
      |        import_datetime: "2013-03-24 17:48:35",
      |        trending_datetime: "1970-01-01 00:00:00",
      |        images: {
      |            fixed_height: {
      |                url: "http://media3.giphy.com/media/wWAIKcFASEFz2/200.gif",
      |                width: "358",
      |                height: "200",
      |                size: "126220",
      |                mp4: "http://media2.giphy.com/media/wWAIKcFASEFz2/200.mp4",
      |                mp4_size: "10967",
      |                webp: "http://media2.giphy.com/media/wWAIKcFASEFz2/200.webp",
      |                webp_size: "186460"
      |            },
      |            fixed_height_still: {
      |                url: "http://media2.giphy.com/media/wWAIKcFASEFz2/200_s.gif",
      |                width: "358",
      |                height: "200"
      |            },
      |            "looping": {
      |                "mp4": "https://media1.giphy.com/media/9COaC32gDyqKk/giphy-loop.mp4"
      |            }
      |        }
      |}""".stripMargin

  val SearchResponse =
    s"""{
      |   "data":[ $SampleGifResponse, $SampleGifResponse ],
      |   "meta":{
      |      "status":200,
      |      "msg":"OK"
      |   },
      |   "pagination":{
      |      "total_count":2143,
      |      "count":25,
      |      "offset":0
      |   }
      |}""".stripMargin


  def searchResponse: Response = Response(Response.HttpStatus(200), StringResponse(SearchResponse))

  def searchJsonResponse: Response = Response(Response.HttpStatus(200), JsonObjectResponse(new JSONObject(SearchResponse)))

  def notFound: Response = Response(Response.HttpStatus(404))

  def mockedClient(responses: Response*) = new GiphyClient(new EmptyClient {
      private var idx = 0

      override def apply[A](r: Request[A]): CancellableFuture[Response] = {
        val resp = responses(idx)
        idx += 1
        CancellableFuture.successful(resp)
      }
    }
  )
}
