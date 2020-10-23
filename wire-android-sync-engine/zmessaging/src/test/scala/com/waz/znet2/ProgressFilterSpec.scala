package com.waz.znet2

import com.waz.specs.AndroidFreeSpec
import com.waz.znet2.http.HttpClient.ProgressFilter

class ProgressFilterSpec extends AndroidFreeSpec {

  feature("ProgressFilter with step count") {

    scenario("given bytes per step is less than 1, when needPublishProgress is called, then accepts increase of at least 1 byte per call") {
      val numberOfSteps = 10
      val totalSize = 3L //bytes

      //bytes per step is 3/10 = 0.3 but adjusted to 1.
      val progressFilter = ProgressFilter.steps(numberOfSteps, totalSize)

      progressFilter.needPublishProgress(1L, total = Some(totalSize)) shouldBe true
      progressFilter.needPublishProgress(1.2.toLong, total = Some(totalSize)) shouldBe false
      progressFilter.needPublishProgress(2.2.toLong, total = Some(totalSize)) shouldBe true
    }

    scenario("given bytes per step is >= 1, when needPublishProgress is called, then accepts increase of at least original size") {
      val numberOfSteps = 10
      val totalSize = 300L //bytes

      //should not accept less than 30 bytes per step
      val progressFilter = ProgressFilter.steps(numberOfSteps, totalSize)

      progressFilter.needPublishProgress(30L, total = Some(totalSize)) shouldBe true
      progressFilter.needPublishProgress(45L, total = Some(totalSize)) shouldBe false
      progressFilter.needPublishProgress(80L, total = Some(totalSize)) shouldBe true
    }
  }
}
