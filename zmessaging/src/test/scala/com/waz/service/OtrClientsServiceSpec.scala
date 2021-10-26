package com.waz.service

import com.waz.content.{OtrClientsStorage, UserPreferences}
import com.waz.content.UserPreferences.ShouldPostClientCapabilities
import com.waz.model.{Domain, SyncId, UserId}
import com.waz.model.otr.ClientId
import com.waz.service.otr.OtrClientsServiceImpl
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.TestUserPreferences

import scala.concurrent.Future

class OtrClientsServiceSpec extends AndroidFreeSpec {

  private val selfUserId = UserId("selfUserId")
  private val selfClientId = ClientId("selfClientId")
  private val currentDomain = Domain("staging.zinfra.io")
  private val userPrefs = new TestUserPreferences()
  private val storage = mock[OtrClientsStorage]
  private val sync = mock[SyncServiceHandle]

  private def createService(): OtrClientsServiceImpl =
    new OtrClientsServiceImpl(
      selfUserId,
      currentDomain,
      selfClientId,
      userPrefs,
      storage,
      sync,
      accounts
    )

  feature("Post client capablitlies") {

    scenario("it posts capabilities if needed") {
      // Given
      result(userPrefs(ShouldPostClientCapabilities) := true)

      // Expectations
      (sync.postClientCapabilities _)
        .expects()
        .once()
        .returning(Future.successful(SyncId("syncId")))

      // When (the service subscribes to the preference upon init)
      createService()
    }

    scenario("it does not post capabilities if not needed") {
      // Given
      result(userPrefs(ShouldPostClientCapabilities) := false)

      // Expectations
      (sync.postClientCapabilities _)
        .expects()
        .never()

      // When (the service subscribes to the preference upon init)
      createService()
    }
  }
}
