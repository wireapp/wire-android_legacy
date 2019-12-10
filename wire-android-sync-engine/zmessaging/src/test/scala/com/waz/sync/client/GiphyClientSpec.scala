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

import com.waz.ZIntegrationMockSpec
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.utils.RichSeq
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request}
import org.junit.runner.RunWith
import org.scalatest.Ignore
import org.scalatest.junit.JUnitRunner

import scala.concurrent.ExecutionContext.Implicits.global

@Ignore
@RunWith(classOf[JUnitRunner])
class GiphyClientSpec extends ZIntegrationMockSpec with DerivedLogTag {

  implicit lazy val urlCreator : Request.UrlCreator = mock[UrlCreator]
  implicit lazy val httpClient = mock[HttpClient]
  implicit lazy val authRequestInterceptor = mock[AuthRequestInterceptor]

  private lazy val giphyClient = new GiphyClientImpl()

  private val testLimit = 5
  private val testOffset = testLimit
  private val testSearchKeyword = "green"

  feature("Giphy http requests") {

    scenario("loading trending with limit") {
      for {
        result <- giphyClient.trending(limit = testLimit, offset = 0)
        _ = verbose(l"Loading trending with limit result: $result")
      } yield result.data.size shouldBe testLimit
    }

    scenario("loading trending with offset") {
      for {
        withZeroOffsetResult <- giphyClient.trending(offset = 0, limit = testLimit)
        withZeroOffset = withZeroOffsetResult.data.map(_.images)
        _ = verbose(l"Loading trending with offset=0 result: $withZeroOffset")
        withTestOffsetResult <- giphyClient.trending(testOffset, testLimit)
        withTestOffset = withTestOffsetResult.data.map(_.images)
        _ = verbose(l"Loading trending with offset=$testOffset result: $withTestOffset")
      } yield (withZeroOffset ++ withTestOffset).distinctBy(_.original).size should be > withZeroOffset.size
    }

    scenario("searching by keyword with limit") {
      for {
        result <- giphyClient.search(testSearchKeyword, limit = testLimit, offset = 0)
        _ = verbose(l"Searching by keyword with limit result: $result")
      } yield result.data.size shouldBe testLimit
    }

    scenario("searching by keyword with offset") {
      for {
        withZeroOffsetResult <- giphyClient.search(testSearchKeyword, offset = 0, limit = testLimit)
        withZeroOffset = withZeroOffsetResult.data.map(_.images)
        _ = verbose(l"Searching by keyword with offset=0 result: $withZeroOffset")
        withTestOffsetResult <- giphyClient.search(testSearchKeyword, testOffset, testLimit)
        withTestOffset = withTestOffsetResult.data.map(_.images)
        _ = verbose(l"Searching by keyword with offset=$testOffset result: $withTestOffset")
      } yield (withZeroOffset ++ withTestOffset).distinctBy(_.original).size should be > withZeroOffset.size
    }

  }


}
