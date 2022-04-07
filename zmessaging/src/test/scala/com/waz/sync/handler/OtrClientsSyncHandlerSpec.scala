package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.content.UserPreferences.ShouldPostClientCapabilities
import com.waz.model.{Domain, QualifiedId, UserId}
import com.waz.model.otr.{Client, ClientId, OtrClientIdMap, QOtrClientIdMap, UserClients}
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service.BackendConfigFixture
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
import com.waz.zms.BuildConfig

class OtrClientsSyncHandlerSpec extends AndroidFreeSpec {
  val federationSupported: Boolean = false

  private val selfUserId = UserId("selfUserId")
  private val selfClientId = ClientId("selfClientId")
  private val currentDomain = Domain("staging.zinfra.io")
  private val netClient = mock[OtrClient]
  private val otrClients = mock[OtrClientsService]
  private val cryptoBox =  mock[CryptoBoxService]
  private val cryptoBoxSessions = mock[CryptoSessionService]
  private val userPrefs = new TestUserPreferences()

  private val otherUserId = UserId("otherUserId")
  private val otherQualifiedId = QualifiedId(otherUserId, currentDomain.str)
  private val otherClientId = ClientId("otherClientId")

  private def createHandler() = new OtrClientsSyncHandlerImpl(
    selfUserId,
    currentDomain,
    BackendConfigFixture.backendSignal,
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
      val responsePreKey = new PreKey(0, Array[Byte](0))
      val responseUserClients = UserClients(otherUserId, Map(otherClientId -> Client(otherClientId)))

      val qClients = QOtrClientIdMap.from(otherQualifiedId -> Set(otherClientId))
      val clients = OtrClientIdMap.from(otherUserId -> Set(otherClientId))

      if (federationSupported) {
        val response: Either[ErrorResponse, Map[QualifiedId, Map[ClientId, PreKey]]] =
          Right(Map(otherQualifiedId -> Map(otherClientId -> responsePreKey)))
        (netClient.loadPreKeys(_: QOtrClientIdMap)).expects(qClients).once().returning(
          CancellableFuture.successful(response)
        )
      } else {
        val response: Either[ErrorResponse, Map[UserId, Seq[(ClientId, PreKey)]]] =
          Right(Map(otherUserId -> Seq(otherClientId -> responsePreKey)))
        (netClient.loadPreKeys(_: OtrClientIdMap)).expects(clients).once().returning {
          CancellableFuture.successful(response)
        }
      }

      (otrClients.updateUserClients(_: Map[UserId, Seq[Client]], _: Boolean)).expects(*, *).once().returning(
        Future.successful(Set(responseUserClients))
      )
      (cryptoBox.sessions _).expects().anyNumberOfTimes().returning(cryptoBoxSessions)
      (cryptoBoxSessions.getOrCreateSession _).expects(*, *).anyNumberOfTimes().returning(
        Future.successful(Option.empty[CryptoSession])
      )

      result(handler.syncSessions(qClients)) shouldEqual Option.empty[ErrorResponse]
    }
  }

  scenario("sync more clients than the request limit") {
    // Given
    val handler = createHandler()
    val clients =
      QOtrClientIdMap(
        (0 to (OtrClientsSyncHandlerImpl.LoadPreKeysMaxClients/4 + 1)).map { _ =>
          QualifiedId(UserId(), currentDomain.str) -> Set(ClientId(), ClientId(), ClientId(), ClientId())
        }.toMap
      )
    val responsePreKey = new PreKey(0, Array[Byte](0))

    val responseUserClients = UserClients(otherUserId, Map(otherClientId -> Client(otherClientId)))
    if (federationSupported) {
      (netClient.loadPreKeys(_: QOtrClientIdMap)).expects(*).atLeastOnce().onCall { cs: QOtrClientIdMap =>
        (cs.size < OtrClientsSyncHandlerImpl.LoadPreKeysMaxClients) shouldBe true
        val result = cs.entries.map { case (qId, clientIds) => qId -> clientIds.map(_ -> responsePreKey).toMap }
        val response: Either[ErrorResponse, Map[QualifiedId, Map[ClientId, PreKey]]] = Right(result)
        CancellableFuture.successful(response)
      }
    } else {
      (netClient.loadPreKeys(_: OtrClientIdMap)).expects(*).atLeastOnce().onCall { cs: OtrClientIdMap =>
        (cs.size < OtrClientsSyncHandlerImpl.LoadPreKeysMaxClients) shouldBe true
        val response: Either[ErrorResponse, Map[UserId, Seq[(ClientId, PreKey)]]] =
          Right(Map(otherUserId -> Seq((otherClientId, responsePreKey))))
        CancellableFuture.successful(response)
      }
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
