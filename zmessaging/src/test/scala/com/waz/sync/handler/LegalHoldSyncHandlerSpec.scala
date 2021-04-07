package com.waz.sync.handler

import com.waz.model.otr.ClientId
import com.waz.model.{LegalHoldRequest, TeamId, UserId}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.LegalHoldClient
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.PreKey
import LegalHoldSyncHandlerSpec._
import com.waz.api.impl.ErrorResponse
import com.wire.signals.CancellableFuture

class LegalHoldSyncHandlerSpec extends AndroidFreeSpec {

  private val client = mock[LegalHoldClient]

  feature("Fetching a legal hold request") {

    scenario("It fetches the legal hold request if the user is a team member") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(Some(TeamId("team1")), UserId("user1"), client)

      (client.fetchLegalHoldRequest _)
        .expects(TeamId("team1"), UserId("user1"))
        .once()
        .returning(CancellableFuture.successful(Right(Some(legalHoldRequest))))

      // When
      val actualResult = result(syncHandler.fetchLegalHoldRequest())

      // Then
      actualResult.isRight shouldBe true
      actualResult.right.get.isDefined shouldBe true
    }

    scenario("It returns none if the user is not a team member") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(None, UserId("user1"), client)

      // When
      val actualResult = result(syncHandler.fetchLegalHoldRequest())

      // Then
      actualResult.isRight shouldBe true
      actualResult.right.get.isEmpty shouldBe true
    }

    scenario("It fails the request fails") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(Some(TeamId("team1")), UserId("user1"), client)
      val error = ErrorResponse(400, "", "")

      (client.fetchLegalHoldRequest _)
        .expects(TeamId("team1"), UserId("user1"))
        .once()
        .returning(CancellableFuture.successful(Left(error)))

      // When
      val actualResult = result(syncHandler.fetchLegalHoldRequest())

      // Then
      actualResult.isLeft shouldBe true
      actualResult.left.get shouldEqual error
    }

  }

  feature("Approving a legal hold request") {

    scenario("It succeeds if the password is correct") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(Some(TeamId("team1")), UserId("user1"), client)

      (client.approveRequest _)
        .expects(TeamId("team1"), UserId("user1"), Some("123"))
        .once()
        .returning(CancellableFuture.successful(Right({})))

      // When
      val actualResult = result(syncHandler.approveRequest(Some("123")))

      // Then
      actualResult.isRight shouldBe true
    }

    scenario("It fails if the password is incorrect") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(Some(TeamId("team1")), UserId("user1"), client)
      val error = ErrorResponse(400, "", "access-denied")

      (client.approveRequest _)
        .expects(TeamId("team1"), UserId("user1"), Some("123"))
        .once()
        .returning(CancellableFuture.successful(Left(error)))

      // When
      val actualResult = result(syncHandler.approveRequest(Some("123")))

      // Then
      actualResult shouldBe Left(LegalHoldError.InvalidPassword)
    }

    scenario("It fails if the password is invalid") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(Some(TeamId("team1")), UserId("user1"), client)
      val error = ErrorResponse(400, "", "invalid-payload")

      (client.approveRequest _)
        .expects(TeamId("team1"), UserId("user1"), Some("123"))
        .once()
        .returning(CancellableFuture.successful(Left(error)))

      // When
      val actualResult = result(syncHandler.approveRequest(Some("123")))

      // Then
      actualResult shouldBe Left(LegalHoldError.InvalidPassword)
    }

    scenario("It fails if the self user is not in a team") {
      // Given
      val syncHandler = new LegalHoldSyncHandlerImpl(teamId = None, UserId("user1"), client)

      // When
      val actualResult = result(syncHandler.approveRequest(Some("123")))

      // Then
      actualResult shouldBe Left(LegalHoldError.NotInTeam)
    }

  }

}

object LegalHoldSyncHandlerSpec {

  val legalHoldRequest: LegalHoldRequest = LegalHoldRequest(
    ClientId("abc"),
    new PreKey(123, AESUtils.base64("oENwaFy74nagzFBlqn9nOQ=="))
  )

}
