package com.waz.model2.transport.responses

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(classOf[RobolectricTestRunner])
class DomainVerificationResponseSpec {

  @Test
  def `given response has config_json_url responseDecoder returns DomainSuccessful with given url`(): Unit = {
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

  @Test
  def `given response has no config_json_url, responseDecoder returns DomainNotFound type`(): Unit = {
    val jsonObject = new JSONObject("{}")

    val response = DomainVerificationResponse.DomainVerificationResponseDecoder.apply(jsonObject)

    assert(response == DomainNotFound)
  }

}
