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

import com.waz.RobolectricUtils
import com.waz.utils.wrappers.URI
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.{AssetData, Dim2, Mime}
import com.waz.service.images.ImageAssetGenerator
import com.waz.sync.client.GiphyClient
import com.waz.testutils.Matchers._
import com.waz.threading.CancellableFuture
import com.waz.znet.ZNetClient.EmptyClient
import org.scalatest._

@Ignore class GiphyServiceSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils { test =>
  var loadRandomResult: (Option[AssetData], AssetData) = (None, AssetData.Empty)
  val biggestRandomResult = AssetData(metaData = Some(Image(Dim2(500, 256), Medium)), mime = Mime.Image.Gif, source =  Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/giphy.gif")))
  val smallestRandomResult = AssetData(metaData = Some(Image(Dim2(100, 51), Preview)), mime = Mime.Image.Gif, source =  Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/100w_s.gif")))
  val randomResult = (Some(smallestRandomResult), biggestRandomResult)

  var loadSearchResult: Seq[(Option[AssetData], AssetData)] = Nil
  var keyword: String = ""
  val biggestSearchResult = AssetData(sizeInBytes = ImageAssetGenerator.MaxGifSize - 1, metaData = Some(Image(Dim2(391, 200), Medium)), mime = Mime.Image.Gif, source =  Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/200_d.gif")))
  val smallestSearchResult = AssetData(metaData = Some(Image(Dim2(100, 120), Preview)), mime = Mime.Image.Gif, source =  Some(URI.parse("http://s3.amazonaws.com/giphygifs/media/Ggjwvmqktuvf2/200_d.gif")))
  val searchResult = Seq((Some(smallestSearchResult), biggestSearchResult))

  lazy val service = new GiphyService(new GiphyClient(new EmptyClient) {
    override def search(keyword: String, offset: Int, limit: Int) = {
      test.keyword = keyword
      CancellableFuture.successful(loadSearchResult)
    }
  })

  feature("translate") {
    scenario("success") {
      loadSearchResult = searchResult
      val keyword = "test"
      service.searchGiphyImage(keyword) should eventually(beMatching({
        case Seq((Some(`smallestSearchResult`), `biggestSearchResult`)) => true
      }))
      test.keyword shouldEqual keyword
    }

    scenario("failed") {
      loadSearchResult = Nil
      service.searchGiphyImage("") should eventually(be(Nil))
    }
  }
}
