package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.content.OtrClientsStorage
import com.waz.model.otr.{Client, ClientId, OtrClientIdMap, UserClients}
import com.waz.model.{LegalHoldRequest, RConvId, SyncId, TeamId, UserId}
import com.waz.service.{LegalHoldService, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.{SyncRequestService, SyncResult}
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
  private val userService = mock[UserService]
  private val clientsStorage = mock[OtrClientsStorage]
  private val otrSync = mock[OtrSyncHandler]
  private val syncRequestService = mock[SyncRequestService]

  def createSyncHandler(teamId: Option[String], userId: String): LegalHoldSyncHandlerImpl =
    new LegalHoldSyncHandlerImpl(
      teamId.map(TeamId.apply),
      UserId(userId),
      client,
      service,
      userService,
      clientsStorage,
      otrSync,
      syncRequestService
    )

  feature("Fetching a legal hold request") {

    scenario("It fetches the request and notifies the service if it exists") {
      // Given
      val syncHandler = createSyncHandler(Some("team1"), "user1")

      (client.fetchLegalHoldRequest _)
        .expects(TeamId("team1"), UserId("user1"))
        .once()
        .returning(CancellableFuture.successful(Right(Some(legalHoldRequest))))

      (service.onLegalHoldRequestSynced _)
        .expects(Some(legalHoldRequest))
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(syncHandler.syncLegalHoldRequest())

      // Then
      actualResult shouldBe SyncResult.Success
    }

    scenario("It fetches the request and notifies the service if none fetched") {
      // Given
      val syncHandler = createSyncHandler(Some("team1"), "user1")

      (client.fetchLegalHoldRequest _)
        .expects(TeamId("team1"), UserId("user1"))
        .once()
        .returning(CancellableFuture.successful(Right(None)))

      (service.onLegalHoldRequestSynced _)
        .expects(None)
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(syncHandler.syncLegalHoldRequest())

      // Then
      actualResult shouldBe SyncResult.Success
    }

    scenario("It returns none if the user is not a team member") {
      // Given
      val syncHandler = createSyncHandler(teamId = None, "user1")

      // When
      val actualResult = result(syncHandler.syncLegalHoldRequest())

      // Then
      actualResult shouldBe SyncResult.Success
    }

    scenario("It fails if the request fails") {
      // Given
      val syncHandler = createSyncHandler(Some("team1"), "user1")
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

  feature("Sync clients for legal hold verification") {

    scenario("It successfully discovers clients") {
      // Given
      val syncHandler = createSyncHandler(Some("team1"), "user1")
      val convId = RConvId("convId")

      val user1 = UserId("user1")
      val user2 = UserId("user2")

      val client1 = Client(ClientId("client1"))
      val client2 = Client(ClientId("client2"))

      val clientList = OtrClientIdMap.from(
        user1 -> Set(client1.id),
        user2 -> Set(client2.id)
      )

      // Expectations
      (otrSync.postClientDiscoveryMessage(_ : RConvId))
        .expects(convId)
        .once()
        .returning(CancellableFuture.successful(Right(clientList)))

      val syncId1 = SyncId("syncId1")

      (userService.syncIfNeeded _)
        .expects(Set(user1, user2), *, *)
        .once()
        .returning(Future.successful(Some(syncId1)))

      val syncId2 = SyncId("syncId2")

      (userService.syncClients(_: Set[UserId]))
        .expects(Set(user1, user2))
        .once()
        .returning(Future.successful(syncId2))

      (syncRequestService.await(_: Set[SyncId]))
        .expects(Set(syncId1, syncId2))
        .once().
        returning(Future.successful(Set(SyncResult.Success, SyncResult.Success)))

      (service.updateLegalHoldStatusAfterFetchingClients _)
        .expects()
        .once()
        .returning(Future.successful(()))

      // When
      val actualResult = result(syncHandler.syncClientsForLegalHoldVerification(convId))

      // Then
      actualResult shouldEqual SyncResult.Success
    }

    scenario("It passes empty client list if discovery fails") {
      // Given
      val syncHandler = createSyncHandler(Some("team1"), "user1")
      val convId = RConvId("convId")
      val errorResponse = ErrorResponse(400, "", "")

      // Expectations
      (otrSync.postClientDiscoveryMessage(_: RConvId))
        .expects(convId)
        .once()
        .returning(CancellableFuture.successful(Left(errorResponse)))

      (service.updateLegalHoldStatusAfterFetchingClients _)
        .expects()
        .once()
        .returning(Future.successful(()))

      // When
      val actualResult = result(syncHandler.syncClientsForLegalHoldVerification(convId))

      // Then
      actualResult shouldEqual SyncResult.Failure(errorResponse)
    }

  }

}

object LegalHoldSyncHandlerSpec {

  val legalHoldRequest: LegalHoldRequest = LegalHoldRequest(
    ClientId("abc"),
    new PreKey(123, AESUtils.base64("oENwaFy74nagzFBlqn9nOQ=="))
  )

}
