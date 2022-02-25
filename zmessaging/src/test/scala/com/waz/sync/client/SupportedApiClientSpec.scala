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


import org.json.{JSONArray, JSONObject}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FeatureSpec, Matchers}

@RunWith(classOf[JUnitRunner])
class SupportedApiClientSpec extends FeatureSpec with Matchers {

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

  feature("Response parsing") {
    scenario("Parse API version response (all fields)") {

      val response = SupportedApiClient.Decoder(clientResponse(List(2,3), Option(true), Option("foo.com")))

      response.supported shouldEqual List(2,3)
      response.federation shouldEqual true
      response.domain shouldEqual "foo.com"
    }

    scenario("Parse API version response (no fields)") {

      val response = SupportedApiClient.Decoder(clientResponse(List(3,45), Option.empty, Option.empty))

      response.supported shouldEqual List(3,45)
      response.federation shouldEqual false
      response.domain shouldEqual ""
    }

    scenario("Parse API version response (empty list)") {

      val response = SupportedApiClient.Decoder(clientResponse(List.empty, Option.empty, Option.empty))
      response.supported shouldEqual List.empty
    }

    scenario("Find common API version (one version)") {
      val response = SupportedApiClient.Decoder(clientResponse(List(3), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(3)) shouldEqual Option(3)
    }

    scenario("Find common API version (no common version)") {
      val response = SupportedApiClient.Decoder(clientResponse(List(3,4,9), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(1, 14, 999)) shouldEqual Option.empty
    }

    scenario("Find common API version (highest supported)") {
      val response = SupportedApiClient.Decoder(clientResponse(List(3,4,9), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(1, 4, 9, 15)) shouldEqual Option(9)
    }

    scenario("Find common API version (lowest supported)") {
      val response = SupportedApiClient.Decoder(clientResponse(List(3,4,9), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(1, 2, 3, 6)) shouldEqual Option(3)
    }

    scenario("Find common API version (middle supported)") {
      val response = SupportedApiClient.Decoder(clientResponse(List(3,4,9), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(4, 10)) shouldEqual Option(4)
    }

    scenario("Find common API version (zero)") {
      val response = SupportedApiClient.Decoder(clientResponse(List(0, 1), Option.empty, Option.empty))
      response.highestCommonAPIVersion(Set(0, 10)) shouldEqual Option(0)
    }

  }
}
