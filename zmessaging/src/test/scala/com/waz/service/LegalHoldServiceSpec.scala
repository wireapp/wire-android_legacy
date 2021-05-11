package com.waz.service

import com.waz.api.impl.ErrorResponse
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage, UserPreferences}
import com.waz.model.ConversationData.LegalHoldStatus
import com.waz.model.ConversationData.LegalHoldStatus.{Disabled, Enabled, PendingApproval}
import com.waz.model.GenericContent.Text
import com.waz.model.otr.Client.DeviceClass
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.model._
import com.waz.service.EventScheduler.{Sequential, Stage}
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.LegalHoldClient
import com.waz.sync.handler.LegalHoldError
import com.waz.testutils.TestUserPreferences
import com.waz.utils.JsonEncoder
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.PreKey
import com.wire.signals.{CancellableFuture, EventStream, Signal}
import org.threeten.bp.Instant

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
  private val sync = mock[SyncServiceHandle]

  var service: LegalHoldServiceImpl = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    // The service reacts to these event streams so we need to mock them,
    // but we return empty streams and instead test the methods directly.

    (clientsStorage.onChanged _)
      .expects()
      .once()
      .returning(EventStream())

    (membersStorage.onAdded _)
      .expects()
      .once()
      .returning(EventStream())

    (membersStorage.onDeleted _)
      .expects()
      .once()
      .returning(EventStream())

    service = new LegalHoldServiceImpl(
      selfUserId,
      Some(teamId),
      userPrefs,
      apiClient,
      clientsService,
      clientsStorage,
      convsStorage,
      membersStorage,
      cryptoSessionService,
      sync
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

  feature("Update legal hold status") {
    // Given
    val user1 = UserId("user1")
    val user2 = UserId("user2")
    val client1 = Client(ClientId("client1"), "")
    val client2 = Client(ClientId("client2"), "")
    val convId = ConvId("conv1")

    scenario("for specific conversation") {
      // Expectations
      setUpExpectationsForConversationUpdate()

      // When
      result(service.updateLegalHoldStatus(Seq(convId)))
    }

    scenario("on clients changed") {
      // Given
      val userClients = UserClients(user1, Map(client1.id -> client1))

      // Expectations
      (membersStorage.getActiveConvs _)
        .expects(user1)
        .once()
        .returning(Future.successful(Seq(convId)))

      setUpExpectationsForConversationUpdate()

      // When
      result(service.onClientsChanged(Seq(userClients)))
    }

    scenario("after fetching clients for verification") {
      // Given
      val userClients = UserClients(user1, Map(client1.id -> client1))
      service.isVerifyingLegalHold = true

      // Expectations
      (membersStorage.getActiveConvs _)
        .expects(user1)
        .once()
        .returning(Future.successful(Seq(convId)))

      setUpExpectationsForConversationUpdate()

      // When
      result(service.updateLegalHoldStatusAfterFetchingClients(Seq(userClients)))

      // Then
      service.isVerifyingLegalHold shouldBe false
    }

    def setUpExpectationsForConversationUpdate(): Unit = {
      // Get the active users of the conversations.
      (membersStorage.getActiveUsers _)
        .expects(convId)
        .once()
        .returning(Future.successful(Seq(user1, user2)))

      // And all of their clients.
      (clientsStorage.getClients _)
        .expects(user1)
        .once()
        .returning(Future.successful(Seq(client1)))

      (clientsStorage.getClients _)
        .expects(user2)
        .once()
        .returning(Future.successful(Seq(client2)))

      // Finally update the conversation.
      (convsStorage.updateAll2 _)
        .expects(Seq(convId), *)
        .once()
        // The return value is not important.
        .returning(Future.successful(Seq()))
    }

  }

  feature("it calculates correct legal hold status") {

    scenario("existing status is disabled") {
      assert(existingStatus = Disabled, detectedLegalHoldDevice = true, expectation = PendingApproval)
      assert(existingStatus = Disabled, detectedLegalHoldDevice = false, expectation = Disabled)
    }

    scenario("existing status is pending approval") {
      assert(existingStatus = PendingApproval, detectedLegalHoldDevice = true, expectation = PendingApproval)
      assert(existingStatus = PendingApproval, detectedLegalHoldDevice = false, expectation = Disabled)
    }

    scenario("existing status is enabled") {
      assert(existingStatus = Enabled, detectedLegalHoldDevice = true, expectation = Enabled)
      assert(existingStatus = Enabled, detectedLegalHoldDevice = false, expectation = Disabled)
    }

    def assert(existingStatus: LegalHoldStatus,
               detectedLegalHoldDevice: Boolean,
               expectation: LegalHoldStatus): Unit = {
      // Given
      val conv = ConversationData(legalHoldStatus = existingStatus)

      // When
      val result = conv.withNewLegalHoldStatus(detectedLegalHoldDevice)

      // Then
      result.legalHoldStatus shouldEqual expectation
    }

  }

  feature("Generic message hints") {

    scenario("it triggers client sync if it discovers legal hold to be enabled") {
      // Given
      val pipeline = createMessageEventPipeline()
      val conv = ConversationData(ConvId("convId"), RConvId("convId"), legalHoldStatus = Disabled)
      val message = createMessage(Messages.LegalHoldStatus.ENABLED)
      val event = createEvent(conv.remoteId, message)

      // Expectations
      (convsStorage.getByRemoteId _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(Some(conv)))

      // Note: we don't care about the return value
      (convsStorage.update _)
        .expects(conv.id, *)
        .once()
        .returning(Future.successful(None))

      (sync.syncClientsForLegalHold _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(SyncId("syncIc")))

      // When
      result(pipeline.apply(Seq(event)))

      // Then
      service.isVerifyingLegalHold shouldBe true
    }

    scenario("it triggers client sync if it discovers legal hold to be disabled") {
      // Given
      val pipeline = createMessageEventPipeline()
      val conv = ConversationData(ConvId("convId"), RConvId("convId"), legalHoldStatus = Enabled)
      val message = createMessage(Messages.LegalHoldStatus.DISABLED)
      val event = createEvent(conv.remoteId, message)

      // Expectations
      (convsStorage.getByRemoteId _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(Some(conv)))

      // Note: we don't care about the return value
      (convsStorage.update _)
        .expects(conv.id, *)
        .once()
        .returning(Future.successful(None))

      (sync.syncClientsForLegalHold _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(SyncId("syncIc")))

      // When
      result(pipeline.apply(Seq(event)))

      // Then
      service.isVerifyingLegalHold shouldBe true
    }

    scenario("it does not trigger client sync if current status and hint are both enabled") {
      // Given
      val pipeline = createMessageEventPipeline()
      val conv = ConversationData(ConvId("convId"), RConvId("convId"), legalHoldStatus = Enabled)
      val message = createMessage(Messages.LegalHoldStatus.ENABLED)
      val event = createEvent(conv.remoteId, message)

      // Expectations
      (convsStorage.getByRemoteId _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(Some(conv)))

      // When
      result(pipeline.apply(Seq(event)))

      // Then
      service.isVerifyingLegalHold shouldBe false
    }

    scenario("it does not trigger client sync if current status and hint are both disabled") {
      // Given
      val pipeline = createMessageEventPipeline()
      val conv = ConversationData(ConvId("convId"), RConvId("convId"), legalHoldStatus = Disabled)
      val message = createMessage(Messages.LegalHoldStatus.DISABLED)
      val event = createEvent(conv.remoteId, message)

      // Expectations
      (convsStorage.getByRemoteId _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(Some(conv)))

      // When
      result(pipeline.apply(Seq(event)))

      // Then
      service.isVerifyingLegalHold shouldBe false
    }

    def createMessageEventPipeline(): EventPipeline = {
      val scheduler = new EventScheduler(Stage(Sequential)(service.messageEventStage))
      new EventPipelineImpl(Vector.empty, scheduler.enqueue)
    }

    def createMessage(status: Messages.LegalHoldStatus): GenericMessage =
      GenericMessage(Uid("messageId"), None, Text("Hello!"))
        .withLegalHoldStatus(status)

    def createEvent(convId: RConvId, message: GenericMessage): GenericMessageEvent =
      GenericMessageEvent(
        convId,
        RemoteInstant(Instant.now()),
        UserId("senderId"),
        message
      )

  }

}

object LegalHoldServiceSpec {

  val legalHoldRequest: LegalHoldRequest = LegalHoldRequest(
    ClientId("abc"),
    new PreKey(123, AESUtils.base64("oENwaFy74nagzFBlqn9nOQ=="))
  )

  val encodedLegalHoldRequest: String = JsonEncoder.encode[LegalHoldRequest](legalHoldRequest).toString

}
