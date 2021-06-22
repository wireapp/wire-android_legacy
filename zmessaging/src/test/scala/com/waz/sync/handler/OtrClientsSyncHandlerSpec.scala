package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.content.UserPreferences.ShouldPostClientCapabilities
import com.waz.model.UserId
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.service.otr.{CryptoBoxService, CryptoSessionService, OtrClientsService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.client.OtrClient
import com.waz.sync.otr.OtrClientsSyncHandlerImpl
import com.waz.testutils.TestUserPreferences
import com.wire.cryptobox.{CryptoSession, PreKey}
import com.wire.signals.CancellableFuture

import scala.collection.immutable.Map
import scala.concurrent.Future

class OtrClientsSyncHandlerSpec extends AndroidFreeSpec {

  private val selfUserId = UserId("selfUserId")
  private val selfClientId = ClientId("selfClientId")
  private val netClient = mock[OtrClient]
  private val otrClients = mock[OtrClientsService]
  private val cryptoBox =  mock[CryptoBoxService]
  private val cryptoBoxSessions = mock[CryptoSessionService]
  private val userPrefs = new TestUserPreferences()

  private val otherUserId = UserId("otherUserId")
  private val otherClientId = ClientId("otherClientId")

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

  feature("sync sessions") {
    scenario("sync one client") {
      // Given
      val handler = createHandler()
      val clients = Map(otherUserId -> Seq(otherClientId))
      val responsePreKey = new PreKey(0, Array[Byte](0))
      val response: Either[ErrorResponse, Map[UserId, Seq[(ClientId, PreKey)]]] =
        Right(Map(otherUserId -> Seq((otherClientId, responsePreKey))))
      val responseUserClients = UserClients(otherUserId, Map(otherClientId -> Client(otherClientId)))

      (netClient.loadPreKeys(_ : Map[UserId, Seq[ClientId]])).expects(clients).once().returning(
        CancellableFuture.successful(response)
      )
      (otrClients.updateUserClients(_: Map[UserId, Seq[Client]], _: Boolean)).expects(*, *).once().returning(
        Future.successful(Set(responseUserClients))
      )
      (cryptoBox.sessions _).expects().anyNumberOfTimes().returning(cryptoBoxSessions)
      (cryptoBoxSessions.getOrCreateSession _).expects(*, *).anyNumberOfTimes().returning(
        Future.successful(Option.empty[CryptoSession])
      )

      result(handler.syncSessions(clients)) shouldEqual Option.empty[ErrorResponse]
    }
  }

  scenario("sync more clients than the request limit") {
    // Given
    val handler = createHandler()
    val clients = (0 to (OtrClientsSyncHandlerImpl.LoadPreKeysMaxClients/4 + 1)).map { _ =>
      UserId() -> Seq(ClientId(), ClientId(), ClientId(), ClientId())
    }.toMap
    val responsePreKey = new PreKey(0, Array[Byte](0))
    val response: Either[ErrorResponse, Map[UserId, Seq[(ClientId, PreKey)]]] =
      Right(Map(otherUserId -> Seq((otherClientId, responsePreKey))))
    val responseUserClients = UserClients(otherUserId, Map(otherClientId -> Client(otherClientId)))

    (netClient.loadPreKeys(_ : Map[UserId, Seq[ClientId]])).expects(*).twice().onCall { cs: Map[UserId, Seq[ClientId]] =>
      (cs.size <  OtrClientsSyncHandlerImpl.LoadPreKeysMaxClients) shouldBe true
      val result = cs.map { case (userId, clientIds) => userId -> clientIds.map(cId => (cId, responsePreKey)) }
      val response: Either[ErrorResponse, Map[UserId, Seq[(ClientId, PreKey)]]] = Right(result)
      CancellableFuture.successful(response)
    }
    (otrClients.updateUserClients(_: Map[UserId, Seq[Client]], _: Boolean)).expects(*, *).once().returning(
      Future.successful(Set(responseUserClients))
    )
    (cryptoBox.sessions _).expects().anyNumberOfTimes().returning(cryptoBoxSessions)
    (cryptoBoxSessions.getOrCreateSession _).expects(*, *).anyNumberOfTimes().returning(
      Future.successful(Option.empty[CryptoSession])
    )

    result(handler.syncSessions(clients)) shouldEqual Option.empty[ErrorResponse]
  }
}
