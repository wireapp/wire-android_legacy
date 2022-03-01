/*
 * Wire
 * Copyright (C) 2022 Wire Swiss GmbH
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


import com.waz.threading.Threading
import com.waz.utils.wrappers.URI
import com.waz.znet2.HttpClientOkHttpImpl
import com.waz.znet2.http.Request.UrlCreator
import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.json.{JSONArray, JSONObject}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers}

import scala.concurrent.Await

@RunWith(classOf[JUnitRunner])
class SupportedApiClientSpec extends FeatureSpec with Matchers with BeforeAndAfter {

  val conf1 = SupportedApiConfig(List(8, 9), true, "foo.bar")
  val conf2 = SupportedApiConfig(List(0, 1, 4), false, "")

  def clientResponse(
                      supported: List[Int],
                      federated: Option[Boolean],
                      domain: Option[String]): JSONObject = {
    val obj = new JSONObject().put("supported", new JSONArray(supported.toArray))
    if(federated.isDefined) {
      obj.put("federation", federated.get)
    }
    if(domain.isDefined) {
      obj.put("domain", domain.get)
    }
    obj
  }

  private var mockServer: MockWebServer = _

  private implicit val urlCreator: UrlCreator = UrlCreator.create(relativeUrl => mockServer.url(relativeUrl).url())

  before {
    mockServer = new MockWebServer()
    mockServer.start()
  }

  after {
    mockServer.shutdown()

  }
  private def createClient(): HttpClientOkHttpImpl = {
    new HttpClientOkHttpImpl(HttpClientOkHttpImpl.createOkHttpClient()(Threading.IO))(Threading.IO)
  }

  feature("Response parsing (HTTP)") {

    scenario("404 error") {

      // GIVEN
      val client = createClient()
      val sut = new SupportedApiClientImpl()(client)

      mockServer.enqueue(
        new MockResponse()
          .setResponseCode(404)
      )

      // WHEN
      val future = sut.getSupportedApiVersions(URI.parse(mockServer.url("").url().toString))

      // THEN
      val result = Await.result(future, 5.seconds)
      result.isRight shouldEqual true
      result.right.map { r =>
        r.supported shouldEqual List(0)
        r.federation shouldEqual false
        r.domain shouldEqual ""
      }
    }

    scenario("Non-404 error") {

      // GIVEN
      val client = createClient()
      val sut = new SupportedApiClientImpl()(client)

      mockServer.enqueue(
        new MockResponse()
          .setResponseCode(500)
      )

      // WHEN
      val future = sut.getSupportedApiVersions(URI.parse(mockServer.url("").url().toString))

      // THEN
      val result = Await.result(future, 5.seconds)
      result.isLeft shouldEqual true
    }

    scenario("JSON response (all fields)") {

      // GIVEN
      val client = createClient()
      val sut = new SupportedApiClientImpl()(client)

      mockServer.enqueue(
        new MockResponse()
          .setResponseCode(200)
          .setHeader("Content-Type", "application/json")
          .setBody(conf1.toString)
      )

      // WHEN
      val future = sut.getSupportedApiVersions(URI.parse(mockServer.url("").url().toString))

      // THEN
      val result = Await.result(future, 5.seconds)
      result.isRight shouldEqual true
      result.right.map { r =>
        r shouldEqual conf1
      }
    }

    scenario("JSON response (only mandatory fields)") {

      // GIVEN
      val client = createClient()
      val sut = new SupportedApiClientImpl()(client)

      mockServer.enqueue(
        new MockResponse()
          .setResponseCode(200)
          .setHeader("Content-Type", "application/json")
          .setBody("""{"supported": [0,1]}""")
      )

      // WHEN
      val future = sut.getSupportedApiVersions(URI.parse(mockServer.url("").url().toString))

      // THEN
      val result = Await.result(future, 5.seconds)
      result.isRight shouldEqual true
      result.right.map { r =>
        r.supported shouldEqual List(0, 1)
      }
    }

    scenario("Check URI") {
      // GIVEN
      val client = createClient()
      val sut = new SupportedApiClientImpl()(client)

      // WHEN
      val future = sut.getSupportedApiVersions(URI.parse(mockServer.url("").url().toString))

      // THEN
      val req = mockServer.takeRequest()
      req.getMethod shouldEqual "GET"
      req.getPath shouldEqual "/api-version"
    }
  }

  feature("Response parsing (JSON)") {
    scenario("Parse API version response (all fields)") {

      val response = SupportedApiConfig.Decoder(clientResponse(List(2,3), Option(true), Option("foo.com")))

      response.supported shouldEqual List(2,3)
      response.federation shouldEqual true
      response.domain shouldEqual "foo.com"
    }

    scenario("Parse API version response (no fields)") {

      val response = SupportedApiConfig.Decoder(clientResponse(List(3,45), Option.empty, Option.empty))

      response.supported shouldEqual List(3,45)
      response.federation shouldEqual false
      response.domain shouldEqual ""
    }

    scenario("Parse API version response (raw object no fields)") {

      val response = SupportedApiConfig.Decoder(new JSONObject().put("supported", new JSONArray(List(3, 45).toArray)))

      response.supported shouldEqual List(3,45)
      response.federation shouldEqual false
      response.domain shouldEqual ""
    }

    scenario("Parse API version response (empty list)") {

      val response = SupportedApiConfig.Decoder(clientResponse(List.empty, Option.empty, Option.empty))
      response.supported shouldEqual List.empty
    }

    scenario("Find common API version (one version)") {
      val response = SupportedApiConfig.Decoder(clientResponse(List(3), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(3)) shouldEqual Option(3)
    }

    scenario("Find common API version (no common version)") {
      val response = SupportedApiConfig.Decoder(clientResponse(List(3,4,9), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(1, 14, 999)) shouldEqual Option.empty
    }

    scenario("Find common API version (highest supported)") {
      val response = SupportedApiConfig.Decoder(clientResponse(List(3,4,9), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(1, 4, 9, 15)) shouldEqual Option(9)
    }

    scenario("Find common API version (lowest supported)") {
      val response = SupportedApiConfig.Decoder(clientResponse(List(3,4,9), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(1, 2, 3, 6)) shouldEqual Option(3)
    }

    scenario("Find common API version (middle supported)") {
      val response = SupportedApiConfig.Decoder(clientResponse(List(3,4,9), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(4, 10)) shouldEqual Option(4)
    }

    scenario("Find common API version (zero)") {
      val response = SupportedApiConfig.Decoder(clientResponse(List(0, 1), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(0, 10)) shouldEqual Option(0)
    }

  }

  feature("Serialize & deserialize") {

    scenario("JSON") {
      SupportedApiConfig.Decoder(SupportedApiConfig.Encoder(conf1)) shouldEqual conf1
      SupportedApiConfig.Decoder(SupportedApiConfig.Encoder(conf2)) shouldEqual conf2
    }

    scenario("String") {
      SupportedApiConfig.fromString(conf1.toString) shouldEqual Some(conf1)
      SupportedApiConfig.fromString(conf2.toString) shouldEqual Some(conf2)
      SupportedApiConfig.fromString("") shouldEqual None
      SupportedApiConfig.fromString("aaa") shouldEqual None
    }
  }
}
