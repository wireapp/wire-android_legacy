package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.content.UserPreferences.ShouldPostClientCapabilities
import com.waz.model.UserId
import com.waz.model.otr.ClientId
import com.waz.service.otr.{CryptoBoxService, OtrClientsService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.client.OtrClient
import com.waz.sync.otr.OtrClientsSyncHandlerImpl
import com.waz.testutils.TestUserPreferences
import com.wire.signals.CancellableFuture

class OtrClientsSyncHandlerSpec extends AndroidFreeSpec {

  private val selfUserId = UserId("selfUserId")
  private val selfClientId = ClientId("selfClientId")
  private val netClient = mock[OtrClient]
  private val otrClients = mock[OtrClientsService]
  private val cryptoBox =  mock[CryptoBoxService]
  private val userPrefs = new TestUserPreferences()

  private def createHandler() = new OtrClientsSyncHandlerImpl(
    selfUserId,
    selfClientId,
    netClient,
    otrClients,
    cryptoBox,
    userPrefs
  )

  feature("Post client capabilities") {

    scenario("It flips the user preference flag after success") {
      // Given
      val handler = createHandler()

      // Expectation
      (netClient.postClientCapabilities _)
        .expects(selfClientId)
        .once()
        .returning(CancellableFuture.successful(Right(())))

      // When
      val actualResult = result(handler.postCapabilities())

      // Then
      actualResult shouldEqual SyncResult.Success
      result(userPrefs(ShouldPostClientCapabilities).apply()) shouldEqual false
    }

    scenario("It does not flip the user preference flag after fail") {
      // Given
      val handler = createHandler()
      val err = ErrorResponse(400, "", "")

      // Expectation
      (netClient.postClientCapabilities _)
        .expects(selfClientId)
        .once()
        .returning(CancellableFuture.successful(Left(err)))

      // When
      val actualResult = result(handler.postCapabilities())

      // Then
      actualResult shouldEqual SyncResult.Failure(err)
      result(userPrefs(ShouldPostClientCapabilities).apply()) shouldEqual true
    }
  }

}
