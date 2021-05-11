package com.waz.service

import com.waz.api.impl.ErrorResponse
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage, UserPreferences}
import com.waz.model.ConversationData.LegalHoldStatus
import com.waz.model.otr.Client.DeviceClass
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.model.{ConvId, ConversationData, LegalHoldDisableEvent, LegalHoldEnableEvent, LegalHoldRequest, LegalHoldRequestEvent, TeamId, UserId}
import com.waz.service.EventScheduler.{Sequential, Stage}
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.handler.LegalHoldError
import com.waz.testutils.TestUserPreferences
import com.waz.utils.JsonEncoder
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.PreKey
import com.wire.signals.{CancellableFuture, Signal}

import scala.concurrent.Future

class LegalHoldServiceSpec extends AndroidFreeSpec {

  import LegalHoldServiceSpec._

  private val selfUserId = UserId("selfUserId")
  private val teamId = TeamId("teamId")
  private val userPrefs = new TestUserPreferences()
  private val apiClient = mock[LegalHoldClient]
  private val clientsService = mock[OtrClientsService]
  private val clientsStorage = mock[OtrClientsStorage]
  private val convsStorage = mock[ConversationStorage]
  private val membersStorage = mock[MembersStorage]
  private val cryptoSessionService = mock[CryptoSessionService]

  var service: LegalHoldServiceImpl = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    service = new LegalHoldServiceImpl(
      selfUserId,
      Some(teamId),
      userPrefs,
      apiClient,
      clientsService,
      clientsStorage,
      convsStorage,
      membersStorage,
      cryptoSessionService
    )

    userPrefs.setValue(UserPreferences.LegalHoldRequest, None)
  }

  // Helpers

  def mockUserDevices(userId: UserId, deviceTypes: Seq[DeviceClass]): Unit = {
    val clients = deviceTypes.map { deviceType =>
      val clientId = ClientId()
      clientId -> Client(clientId, "", deviceClass = deviceType)
    }

    (clientsStorage.optSignal _)
      .expects(userId)
      .once()
      .returning(Signal.const(Some(UserClients(userId, clients.toMap))))
  }

  def mockConversation(convId: ConvId, legalHoldStatus: LegalHoldStatus): Unit =
    (convsStorage.optSignal _)
      .expects(convId)
      .once()
      .returning(Signal.const(Some(ConversationData(convId, legalHoldStatus = legalHoldStatus))))

  def createEventPipeline(): EventPipeline = {
    val scheduler = new EventScheduler(Stage(Sequential)(service.legalHoldEventStage))
    new EventPipelineImpl(Vector.empty, scheduler.enqueue)
  }

  // Tests

  feature("Is legal hold active for user") {

    scenario("with a legal hold device") {
      // Given
      val userId = UserId("user1")
      mockUserDevices(userId, Seq(DeviceClass.Phone, DeviceClass.LegalHold))

      // When
      val actualResult = result(service.isLegalHoldActive(userId).future)

      // Then
      actualResult shouldBe true
    }

    scenario("without a legal hold device") {
      // Given
      val userId = UserId("user1")
      mockUserDevices(userId, Seq(DeviceClass.Phone))

      // When
      val actualResult = result(service.isLegalHoldActive(userId).future)

      // Then
      actualResult shouldBe false
    }
  }

  feature("Is legal hold active for a conversation") {

    scenario("for a conversation with enabled legal hold status") {
      // Given
      val convId = ConvId("conv1")
      mockConversation(convId, LegalHoldStatus.Enabled)

      // When
      val actualResult = result(service.isLegalHoldActive(convId).future)

      // Then
      actualResult shouldBe true
    }

    scenario("for a conversation with pending legal hold status") {
      // Given
      val convId = ConvId("conv1")
      mockConversation(convId, LegalHoldStatus.PendingApproval)

      // When
      val actualResult = result(service.isLegalHoldActive(convId).future)

      // Then
      actualResult shouldBe true
    }

    scenario("for a conversation with disabled legal hold status") {
      // Given
      val convId = ConvId("conv1")
      mockConversation(convId, LegalHoldStatus.Disabled)

      // When
      val actualResult = result(service.isLegalHoldActive(convId).future)

      // Then
      actualResult shouldBe false
    }
  }

  feature("Legal hold users in a conversation") {

    scenario("that contains legal hold subjects") {
      // Given
      val convId = ConvId("conv1")
      val user1 = UserId("user1")
      val user2 = UserId("user2")
      val user3 = UserId("user3")

      (membersStorage.activeMembers _)
        .expects(convId)
        .once()
        .returning(Signal.const(Set(user1, user2, user3)))

      mockUserDevices(user1, Seq(DeviceClass.Phone))
      mockUserDevices(user2, Seq(DeviceClass.Desktop, DeviceClass.LegalHold))
      mockUserDevices(user3, Seq(DeviceClass.Phone, DeviceClass.LegalHold))

      // when
      val actualResult = result(service.legalHoldUsers(convId).future)

      // Then
      actualResult.toSet shouldEqual Set(user2, user3)
    }

    scenario("that does not contain any legal hold subjects") {
      // Given
      val convId = ConvId("conv1")
      val user1 = UserId("user1")
      val user2 = UserId("user2")
      val user3 = UserId("user3")

      (membersStorage.activeMembers _)
        .expects(convId)
        .once()
        .returning(Signal.const(Set(user1, user2, user3)))

      mockUserDevices(user1, Seq(DeviceClass.Phone))
      mockUserDevices(user2, Seq(DeviceClass.Desktop, DeviceClass.Phone))
      mockUserDevices(user3, Seq(DeviceClass.Phone, DeviceClass.Desktop))

      // when
      val actualResult = result(service.legalHoldUsers(convId).future)

      // Then
      actualResult shouldBe empty
    }

  }

  feature("Fetch the legal hold request") {

    scenario("legal hold request exists") {
      // Given
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))

      // When
      val fetchedResult = result(service.legalHoldRequest.head)

      // Then
      fetchedResult shouldBe defined
      fetchedResult.get.clientId.str shouldEqual "abc"
      fetchedResult.get.lastPreKey.id shouldEqual legalHoldRequest.lastPreKey.id
      fetchedResult.get.lastPreKey.data shouldEqual legalHoldRequest.lastPreKey.data
    }

    scenario("legal hold request does not exist") {
      userPrefs.setValue(UserPreferences.LegalHoldRequest, None)

      // When
      val fetchedResult = result(service.legalHoldRequest.head)

      // Then
      fetchedResult shouldEqual None
    }
  }

  feature("Legal hold event processing") {

    scenario("it processes the legal hold request event") {
      // Given
      val pipeline = createEventPipeline()
      val event = LegalHoldRequestEvent(selfUserId, legalHoldRequest)

      // When
      result(pipeline.apply(Seq(event)))

      // Then
      val storedLegalHoldRequest = result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply())
      storedLegalHoldRequest.isDefined shouldBe true
      storedLegalHoldRequest.get.clientId shouldEqual legalHoldRequest.clientId
      storedLegalHoldRequest.get.lastPreKey.id shouldEqual legalHoldRequest.lastPreKey.id
      storedLegalHoldRequest.get.lastPreKey.data shouldEqual legalHoldRequest.lastPreKey.data
    }

    scenario("it ignores a legal hold request event not for the self user") {
      // Given
      val pipeline = createEventPipeline()
      val event = LegalHoldRequestEvent(UserId("someOtherUser"), legalHoldRequest)

      // When
      result(pipeline.apply(Seq(event)))

      // Then
      result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply()) shouldBe None
    }

    scenario("it deletes an existing legal hold request when legal hold is enabled") {
      // Given
      val pipeline = createEventPipeline()
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))

      // When
      result(pipeline.apply(Seq(LegalHoldEnableEvent(selfUserId))))

      // Then
      result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply()) shouldBe None
    }

    scenario("it does not delete an existing legal hold request when legal hold is enabled for another user") {
      // Given
      val pipeline = createEventPipeline()
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))

      // When
      result(pipeline.apply(Seq(LegalHoldEnableEvent(UserId("someOtherUser")))))

      // Then
      result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply()).isEmpty shouldBe false
    }

    scenario("it deletes an existing legal hold request when legal hold is disabled") {
      // Given
      val pipeline = createEventPipeline()
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))

      // When
      result(pipeline.apply(Seq(LegalHoldDisableEvent(selfUserId))))

      // Then
      result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply()) shouldBe None
    }

    scenario("it does not delete an existing legal hold request when legal hold is disabled for another user") {
      // Given
      val pipeline = createEventPipeline()
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))

      // When
      result(pipeline.apply(Seq(LegalHoldDisableEvent(UserId("someOtherUser")))))

      // Then
      result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply()).isEmpty shouldBe false
    }

  }

  feature("Approve legal hold request") {

    scenario("It creates a client and and approves the request") {
      // Given
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))

      mockClientAndSessionCreation()

      // Approve the request.
      (apiClient.approveRequest _)
        .expects(teamId, selfUserId, Some("password"))
        .once()
        .returning(CancellableFuture.successful(Right({})))

      // When
      val actualResult = result(service.approveRequest(legalHoldRequest, Some("password")))

      // Then
      actualResult.isRight shouldBe true
      result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply()) shouldBe None
    }

    scenario("It deletes the client if approval failed") {
      // Given
      val client = mockClientAndSessionCreation()

      // Approve the request.
      (apiClient.approveRequest _)
        .expects(teamId, selfUserId, Some("password"))
        .once()
        .returning(CancellableFuture.successful(Left(ErrorResponse(400, "", "access-denied"))))

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
      val client = Client(legalHoldRequest.clientId, "", deviceClass = DeviceClass.LegalHold)

      // Create the client.
      (clientsService.getOrCreateClient _)
        .expects(selfUserId, legalHoldRequest.clientId)
        .once()
        .returning(Future.successful {
          Client(legalHoldRequest.clientId, "")
        })

      // Saving the client.
      (clientsService.updateUserClients(_: UserId, _: Seq[Client], _: Boolean))
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
