package com.waz.model2.transport.responses

import com.waz.specs.ZSpec
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.scalatest.RobolectricTests

@RunWith(classOf[RobolectricTestRunner])
class DomainVerificationResponseSpec extends ZSpec with RobolectricTests {

  feature("Response parsing") {
    scenario("given response has config_json_url responseDecoder returns DomainSuccessful with given url") {

      val configUrl = "https://wire-rest.https.orange.com/config.json"
      val jsonString =
        s"""
          |{
          |  "config_json_url": "$configUrl",
          |  "webapp_welcome_url": "https://app.wire.orange.com/"
          |}
        """.stripMargin
      val jsonObject = new JSONObject(jsonString)

      val response = DomainVerificationResponse.DomainVerificationResponseDecoder.apply(jsonObject)

      assert(response == DomainSuccessful(configUrl))
    }

    scenario("given response has no config_json_url, responseDecoder returns DomainNotFound type") {
      val jsonObject = new JSONObject("{}")

      val response = DomainVerificationResponse.DomainVerificationResponseDecoder.apply(jsonObject)

      assert(response == DomainNotFound)
    }
  }

}
