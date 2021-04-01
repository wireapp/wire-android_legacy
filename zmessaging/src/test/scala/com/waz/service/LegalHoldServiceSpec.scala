package com.waz.service

import com.waz.content.{PropertiesStorage, PropertyValue}
import com.waz.specs.AndroidFreeSpec
import LegalHoldService._
import com.waz.api.impl.ErrorResponse
import com.waz.model.{LegalHoldRequest, LegalHoldRequestEvent, UserId}
import com.waz.model.otr.ClientId
import com.waz.service.EventScheduler.{Sequential, Stage}
import com.waz.sync.SyncResult
import com.waz.sync.handler.LegalHoldSyncHandler
import com.waz.utils.JsonEncoder
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.PreKey

import scala.concurrent.Future

class LegalHoldServiceSpec extends AndroidFreeSpec {

  import LegalHoldServiceSpec._

  private val selfUserId = UserId("selfUserId")
  private val storage = mock[PropertiesStorage]
  private val syncHandler = mock[LegalHoldSyncHandler]

  feature("Fetch the legal hold request") {

    scenario("legal hold request exists") {
      // Given
      val service = new LegalHoldServiceImpl(selfUserId, storage, syncHandler)
      val value = JsonEncoder.encode[LegalHoldRequest](legalHoldRequest).toString

      (storage.find _)
        .expects(LegalHoldRequestKey)
        .once()
        .returning(Future.successful(Some(PropertyValue(LegalHoldRequestKey, value))))

      // When
      val fetchedResult = result(service.fetchLegalHoldRequest())

      // Then
      fetchedResult shouldBe defined
      fetchedResult.get.clientId.str shouldEqual "abc"
      fetchedResult.get.lastPreKey.id shouldEqual legalHoldRequest.lastPreKey.id
      fetchedResult.get.lastPreKey.data shouldEqual legalHoldRequest.lastPreKey.data
    }

    scenario("legal hold request does not exist") {
      // Given
      val service = new LegalHoldServiceImpl(selfUserId, storage, syncHandler)

      (storage.find _)
        .expects(LegalHoldRequestKey)
        .once()
        .returning(Future.successful(None))

      // When
      val fetchedResult = result(service.fetchLegalHoldRequest())

      // Then
      fetchedResult shouldEqual None
    }
  }

  feature("Legal hold event processing") {

    scenario("it processes the legal hold request event") {
      // Given
      val service = new LegalHoldServiceImpl(selfUserId, storage, syncHandler)
      val scheduler = new EventScheduler(Stage(Sequential)(service.legalHoldRequestEventStage))
      val pipeline  = new EventPipelineImpl(Vector.empty, scheduler.enqueue)
      val event = LegalHoldRequestEvent(selfUserId, legalHoldRequest)

      // Then
      (storage.save _)
        .expects(PropertyValue(LegalHoldRequestKey, encodedLegalHoldRequest))
        .once()
        .returning(Future.successful({}))

      // When
      result(pipeline.apply(Seq(event)))
    }

    scenario("it ignores a legal hold request event not for the self user") {
      // Given
      val service = new LegalHoldServiceImpl(selfUserId, storage, syncHandler)
      val scheduler = new EventScheduler(Stage(Sequential)(service.legalHoldRequestEventStage))
      val pipeline  = new EventPipelineImpl(Vector.empty, scheduler.enqueue)
      val event = LegalHoldRequestEvent(targetUserId = UserId("someOtherUser"), legalHoldRequest)

      // Then
      (storage.save _)
        .expects( PropertyValue(LegalHoldRequestKey, encodedLegalHoldRequest))
        .never()

      // When
      result(pipeline.apply(Seq(event)))
    }
  }

  feature("Sync legal hold request") {

    scenario("it succeeds if legal hold request exists") {
      // Given
      val service = new LegalHoldServiceImpl(selfUserId, storage, syncHandler)

      (syncHandler.fetchLegalHoldRequest _ )
        .expects()
        .once()
        .returning(Future.successful(Right(Some(legalHoldRequest))))

      (storage.save _)
        .expects(PropertyValue(LegalHoldRequestKey, encodedLegalHoldRequest))
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(service.syncLegalHoldRequest())

      // Then
      actualResult shouldEqual SyncResult.Success
    }

    scenario("it succeeds if legal hold does not exist") {
      // Given
      val service = new LegalHoldServiceImpl(selfUserId, storage, syncHandler)

      (syncHandler.fetchLegalHoldRequest _ )
        .expects()
        .once()
        .returning(Future.successful(Right(None)))

      // When
      val actualResult = result(service.syncLegalHoldRequest())

      // Then
      actualResult shouldEqual SyncResult.Success
    }

    scenario("it fails if an error occurs") {
      // Given
      val service = new LegalHoldServiceImpl(selfUserId, storage, syncHandler)
      val error = ErrorResponse(400, "", "")

      (syncHandler.fetchLegalHoldRequest _ )
        .expects()
        .once()
        .returning(Future.successful(Left(error)))

      // When
      val actualResult = result(service.syncLegalHoldRequest())

      // Then
      actualResult shouldEqual SyncResult.Failure(error)
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
