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
package com.waz.service

import com.waz.specs.ZSpec
import com.waz.sync.client.SupportedApiConfig
import com.waz.utils.wrappers.URI
import com.wire.signals.{Signal, SourceSignal}
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

object BackendConfigFixture {

  //This information can be found in downloadable google-services.json file from the BE console.
  val testFirebaseOptions = FirebaseOptions(
    "21312312312",
    "1:7213123213:android:123123213213",
    "dsadasr3ee2ed213123eqw213")

  val testBackend: BackendConfig = BackendConfig(
    environment = "test",
    baseUrl = "https://example.com",
    websocketUrl = "https://example.com",
    blacklistHost = Some("https://example.com"),
    teamsUrl = "https://example.com",
    accountsUrl = "https://example.com",
    websiteUrl = "https://example.com",
    testFirebaseOptions
  )

  val backendSignal: Signal[BackendConfig] = new SourceSignal[BackendConfig](Option(BackendConfigFixture.testBackend))
}

@RunWith(classOf[RobolectricTestRunner])
class BackendConfigSpec extends ZSpec {

  @Test
  def `test No API configuration`(): Unit = {
      // GIVEN
      val baseUrl = "https://wire.example.com"
      val backendConfig = BackendConfig(
        "foo",
        baseUrl,
        "https://ws.example.com",
        None,
        "teams",
        "accounts",
        "website",
        FirebaseOptions("aaa", "bbb", "ccc"))

      // THEN
      backendConfig.apiVersionInformation shouldEqual None
      backendConfig.agreedApiVersion shouldEqual None
      backendConfig.couldNotAgreeOnApiVersion shouldEqual false
      backendConfig.baseUrl shouldEqual URI.parse(baseUrl)
      backendConfig.baseUrlWithApi shouldEqual URI.parse(baseUrl)
      backendConfig.federationSupport.isSupported shouldEqual false
    }

  @Test
  def `test V0 API configuration`(): Unit = {
      // GIVEN
      val baseUrl = "https://wire.example.com"
      val apiVersionConfig = SupportedApiConfig(List(0), federation = false, "example.com")
      val backendConfig = BackendConfig(
        "foo",
        baseUrl,
        "https://ws.example.com",
        None,
        "teams",
        "accounts",
        "website",
        FirebaseOptions("aaa", "bbb", "ccc"),
        apiVersionInformation = Some(apiVersionConfig)
      )

      // THEN
      backendConfig.apiVersionInformation shouldEqual Some(apiVersionConfig)
      backendConfig.agreedApiVersion shouldEqual Some(0)
      backendConfig.couldNotAgreeOnApiVersion shouldEqual false
      backendConfig.baseUrl shouldEqual URI.parse(baseUrl)
      backendConfig.baseUrlWithApi shouldEqual URI.parse(baseUrl)
      backendConfig.federationSupport.isSupported shouldEqual false
    }

  @Test
  def `test V1 API configuration`(): Unit = {
      // GIVEN
      val baseUrl = "https://wire.example.com"
      val apiVersionConfig = SupportedApiConfig(List(0, 1, 9999), federation = true, "example.com")
      val backendConfig = BackendConfig(
        "foo",
        baseUrl,
        "https://ws.example.com",
        None,
        "teams",
        "accounts",
        "website",
        FirebaseOptions("aaa", "bbb", "ccc"),
        apiVersionInformation = Some(apiVersionConfig)
      )

      // THEN
      backendConfig.apiVersionInformation shouldEqual Some(apiVersionConfig)
      backendConfig.agreedApiVersion shouldEqual Some(1)
      backendConfig.couldNotAgreeOnApiVersion shouldEqual false
      backendConfig.baseUrl shouldEqual URI.parse(baseUrl)
      backendConfig.baseUrlWithApi.toString shouldEqual baseUrl+"/v1"
      backendConfig.federationSupport.isSupported shouldEqual true
    }

  def `test non-matching API configuration`(): Unit = {
    // GIVEN
    val baseUrl = "https://wire.example.com"
    val apiVersionConfig = SupportedApiConfig(List(999999), federation = true, "example.com")
    val backendConfig = BackendConfig(
      "foo",
      baseUrl,
      "https://ws.example.com",
      None,
      "teams",
      "accounts",
      "website",
      FirebaseOptions("aaa", "bbb", "ccc"),
      apiVersionInformation = Some(apiVersionConfig)
    )

    // THEN
    backendConfig.apiVersionInformation shouldEqual Some(apiVersionConfig)
    backendConfig.agreedApiVersion shouldEqual None
    backendConfig.couldNotAgreeOnApiVersion shouldEqual true
    backendConfig.baseUrl shouldEqual URI.parse(baseUrl)
    backendConfig.baseUrlWithApi.toString shouldEqual baseUrl+"/v1"
    backendConfig.federationSupport.isSupported shouldEqual true
  }
}
