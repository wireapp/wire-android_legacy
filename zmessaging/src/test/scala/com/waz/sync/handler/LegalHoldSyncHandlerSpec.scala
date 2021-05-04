package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.model.otr.ClientId
import com.waz.model.{LegalHoldRequest, TeamId, UserId}
import com.waz.service.LegalHoldService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.handler.LegalHoldSyncHandlerSpec._
import com.waz.sync.otr.OtrSyncHandler
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.PreKey
import com.wire.signals.CancellableFuture

import scala.concurrent.Future

class LegalHoldSyncHandlerSpec extends AndroidFreeSpec {

  private val client = mock[LegalHoldClient]
  private val service  = mock[LegalHoldService]
  private val otrSync = mock[OtrSyncHandler]

  feature("Fetching a legal hold request") {

    scenario("It fetches and stores the legal hold request if it exists") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(Some(TeamId("team1")), UserId("user1"), client, service, otrSync)

      (client.fetchLegalHoldRequest _)
        .expects(TeamId("team1"), UserId("user1"))
        .once()
        .returning(CancellableFuture.successful(Right(Some(legalHoldRequest))))

      (service.storeLegalHoldRequest _)
        .expects(legalHoldRequest)
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(syncHandler.syncLegalHoldRequest())

      // Then
      actualResult shouldBe SyncResult.Success
    }

    scenario("It deletes the existing legal hold request if none fetched") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(Some(TeamId("team1")), UserId("user1"), client, service, otrSync)

      (client.fetchLegalHoldRequest _)
        .expects(TeamId("team1"), UserId("user1"))
        .once()
        .returning(CancellableFuture.successful(Right(None)))

      (service.deleteLegalHoldRequest _)
        .expects()
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(syncHandler.syncLegalHoldRequest())

      // Then
      actualResult shouldBe SyncResult.Success
    }

    scenario("It returns none if the user is not a team member") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(None, UserId("user1"), client, service, otrSync)

      // When
      val actualResult = result(syncHandler.syncLegalHoldRequest())

      // Then
      actualResult shouldBe SyncResult.Success
    }

    scenario("It fails if the request fails") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(Some(TeamId("team1")), UserId("user1"), client, service, otrSync)
      val error = ErrorResponse(400, "", "")

      (client.fetchLegalHoldRequest _)
        .expects(TeamId("team1"), UserId("user1"))
        .once()
        .returning(CancellableFuture.successful(Left(error)))

      // When
      val actualResult = result(syncHandler.syncLegalHoldRequest())

      // Then
      actualResult shouldBe SyncResult.Failure(error)
    }

  }

}

object LegalHoldSyncHandlerSpec {

  val legalHoldRequest: LegalHoldRequest = LegalHoldRequest(
    ClientId("abc"),
    new PreKey(123, AESUtils.base64("oENwaFy74nagzFBlqn9nOQ=="))
  )

}
