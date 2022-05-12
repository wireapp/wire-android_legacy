package com.waz.sync.client

import com.waz.specs.AndroidFreeSpec

class OtrClientSpec extends AndroidFreeSpec {

  scenario("It uses correct capabilities") {
    // We want to assert that we report the correct capabilities to avoid
    // accidentally removing or editing the values.
    OtrClient.ClientCapabilities shouldEqual Seq("legalhold-implicit-consent")
  }

}
