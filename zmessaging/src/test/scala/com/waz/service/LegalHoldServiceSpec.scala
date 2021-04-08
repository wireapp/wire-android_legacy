package com.waz.service

import com.waz.api.OtrClientType
import com.waz.api.impl.ErrorResponse
import com.waz.content.{PropertiesStorage, PropertyValue}
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.model.{LegalHoldRequest, LegalHoldRequestEvent, UserId}
import com.waz.service.EventScheduler.{Sequential, Stage}
import com.waz.service.LegalHoldService._
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.handler.{LegalHoldError, LegalHoldSyncHandler}
import com.waz.utils.JsonEncoder
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.PreKey
import com.wire.signals.Signal

import scala.concurrent.Future

class LegalHoldServiceSpec extends AndroidFreeSpec {

  import LegalHoldServiceSpec._

  private val selfUserId = UserId("selfUserId")
  private val storage = mock[PropertiesStorage]
  private val syncHandler = mock[LegalHoldSyncHandler]
  private val clientsService = mock[OtrClientsService]
  private val cryptoSessionService = mock[CryptoSessionService]

  private def createService(): LegalHoldService =
    new LegalHoldServiceImpl(selfUserId, storage, syncHandler, clientsService, cryptoSessionService)

  feature("Fetch the legal hold request") {

    scenario("legal hold request exists") {
      // Given
      val service = createService()
      val value = JsonEncoder.encode[LegalHoldRequest](legalHoldRequest).toString

      (storage.optSignal _)
        .expects(LegalHoldRequestKey)
        .once()
        .returning(Signal.const(Some(PropertyValue(LegalHoldRequestKey, value))))

      // When
      val fetchedResult = result(service.legalHoldRequest.head)

      // Then
      fetchedResult shouldBe defined
      fetchedResult.get.clientId.str shouldEqual "abc"
      fetchedResult.get.lastPreKey.id shouldEqual legalHoldRequest.lastPreKey.id
      fetchedResult.get.lastPreKey.data shouldEqual legalHoldRequest.lastPreKey.data
    }

    scenario("legal hold request does not exist") {
      // Given
      val service = createService()

      (storage.optSignal _)
        .expects(LegalHoldRequestKey)
        .once()
        .returning(Signal.const(None))

      // When
      val fetchedResult = result(service.legalHoldRequest.head)

      // Then
      fetchedResult shouldEqual None
    }
  }

  feature("Legal hold event processing") {

    scenario("it processes the legal hold request event") {
      // Given
      val service = createService()
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
      val service = createService()
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
      val service = createService()

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
      val service = createService()

      (syncHandler.fetchLegalHoldRequest _ )
        .expects()
        .once()
        .returning(Future.successful(Right(None)))

      (storage.deleteByKey _)
        .expects(LegalHoldRequestKey)
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(service.syncLegalHoldRequest())

      // Then
      actualResult shouldEqual SyncResult.Success
    }

    scenario("it fails if an error occurs") {
      // Given
      val service = createService()
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

  feature("Approve legal hold request") {

    scenario("It creates a client and and approves the request") {
      // Given
      val service = createService()

      mockClientAndSessionCreation()

      // Approve the request.
      (syncHandler.approveRequest _)
        .expects(Some("password"))
        .once()
        .returning(Future.successful(Right({})))

      // Then pending request is deleted.
      (storage.deleteByKey _)
        .expects(LegalHoldRequestKey)
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(service.approveRequest(legalHoldRequest, Some("password")))

      // Then
      actualResult.isRight shouldBe true
    }

    scenario("It deletes the client if approval failed") {
      // Given
      val service = createService()
      val client = mockClientAndSessionCreation()

      // Approve the request.
      (syncHandler.approveRequest _)
        .expects(Some("password"))
        .once()
        .returning(Future.successful(Left(LegalHoldError.InvalidPassword)))

      // Delete client.
      (clientsService.removeClients _ )
        .expects(selfUserId, Seq(client.id))
        .once()
        // We don't care about the return type.
        .returning(Future.successful(None))

      // Delete session.
      (cryptoSessionService.deleteSession _)
        .expects(SessionId(selfUserId, client.id))
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(service.approveRequest(legalHoldRequest, Some("password")))

      // Then
      actualResult shouldBe Left(LegalHoldError.InvalidPassword)
    }

    def mockClientAndSessionCreation(): Client = {
      val client = Client(legalHoldRequest.clientId, "", devType = OtrClientType.LEGALHOLD)

      // Create the client.
      (clientsService.getOrCreateClient _)
        .expects(selfUserId, legalHoldRequest.clientId)
        .once()
        .returning(Future.successful {
          Client(legalHoldRequest.clientId, "")
        })

      // Saving the client.
      (clientsService.updateUserClients _)
        .expects(selfUserId, Seq(client), false)
        .once()
        .returning(Future.successful {
          UserClients(selfUserId, Map(client.id -> client))
        })

      // Creating the crypto session.
      (cryptoSessionService.getOrCreateSession _)
        .expects(SessionId(selfUserId, client.id), legalHoldRequest.lastPreKey)
        .once()
        // To make testing simpler, just return none since
        // we don't actually need to use the crypto session.
        .returning(Future.successful(None))

      client
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
