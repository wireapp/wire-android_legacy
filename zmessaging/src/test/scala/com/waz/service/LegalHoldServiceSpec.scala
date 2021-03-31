package com.waz.service

import com.waz.content.{PropertiesStorage, PropertyValue}
import com.waz.specs.AndroidFreeSpec
import LegalHoldService._
import com.waz.model.{LegalHoldRequest, LegalHoldRequestEvent}
import com.waz.model.otr.ClientId
import com.waz.service.EventScheduler.{Sequential, Stage}
import com.waz.utils.JsonEncoder
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.PreKey

import scala.concurrent.Future

class LegalHoldServiceSpec extends AndroidFreeSpec {

  import LegalHoldServiceSpec._

  private val storage = mock[PropertiesStorage]

  feature("Fetch the legal hold request") {

    scenario("legal hold request exists") {
      // Given
      val service = new LegalHoldServiceImpl(storage)
      val value = JsonEncoder.encode[LegalHoldRequest](legalHoldRequest).toString

      (storage.find _)
        .expects(LegalHoldRequestKey)
        .once()
        .returning(Future.successful(Some(PropertyValue(LegalHoldRequestKey, value))))

      // When
      val fetchedResult = result(service.fetchLegalHoldRequest)

      // Then
      fetchedResult shouldBe defined
      fetchedResult.get.clientId.str shouldEqual "abc"
      fetchedResult.get.lastPreKey.id shouldEqual legalHoldRequest.lastPreKey.id
      fetchedResult.get.lastPreKey.data shouldEqual legalHoldRequest.lastPreKey.data
    }

    scenario("legal hold request does not exist") {
      // Given
      val service = new LegalHoldServiceImpl(storage)

      (storage.find _)
        .expects(LegalHoldRequestKey)
        .once()
        .returning(Future.successful(None))

      // When
      val fetchedResult = result(service.fetchLegalHoldRequest)

      // Then
      fetchedResult shouldEqual None
    }
  }

  feature("Legal hold event processing") {

    scenario("if processes the legal hold request event") {
      // Given
      val service = new LegalHoldServiceImpl(storage)
      val scheduler = new EventScheduler(Stage(Sequential)(service.legalHoldRequestEventStage))
      val pipeline  = new EventPipelineImpl(Vector.empty, scheduler.enqueue)
      val event = LegalHoldRequestEvent(legalHoldRequest)

      // Then
      (storage.save _)
        .expects( PropertyValue(LegalHoldRequestKey, encodedLegalHoldRequest))
        .once()
        .returning(Future.successful({}))

      // When
      result(pipeline.apply(Seq(event)))
    }
  }
}

object LegalHoldServiceSpec {

  val legalHoldRequest: LegalHoldRequest = LegalHoldRequest(
    ClientId("abc"),
    new PreKey(123, AESUtils.base64("oENwaFy74nagzFBlqn9nOQ=="))
  )

  val encodedLegalHoldRequest: String = JsonEncoder.encode[LegalHoldRequest](legalHoldRequest).toString

}
