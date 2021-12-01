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
import com.waz.service.messages.MessagesService
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

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

class LegalHoldServiceSpec extends AndroidFreeSpec {

  import LegalHoldServiceSpec._

  private val selfUserId = UserId("selfUserId")
  private val selfDomain = Domain("anta.wire.link")
  private val teamId = TeamId("teamId")
  private val userPrefs = new TestUserPreferences()
  private val apiClient = mock[LegalHoldClient]
  private val clientsService = mock[OtrClientsService]
  private val clientsStorage = mock[OtrClientsStorage]
  private val convsStorage = mock[ConversationStorage]
  private val membersStorage = mock[MembersStorage]
  private val cryptoSessionService = mock[CryptoSessionService]
  private val sync = mock[SyncServiceHandle]
  private val messagesService = mock[MessagesService]
  private val userService = mock[UserService]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    userPrefs.setValue(UserPreferences.LegalHoldRequest, None)
  }

  def createService(setUpInitExpectations: Boolean = true): LegalHoldServiceImpl = {
    if (setUpInitExpectations) {
      // The service subscribes to a signal upon initialization,
      // so we need to mock this to keep tests happy.
      (convsStorage.contents _)
        .expects()
        .once()
        .returning(Signal(Map.empty))
    }

    new LegalHoldServiceImpl(
      selfUserId,
      selfDomain,
      Some(teamId),
      userPrefs,
      apiClient,
      clientsService,
      clientsStorage,
      convsStorage,
      membersStorage,
      cryptoSessionService,
      sync,
      messagesService,
      userService
    )
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
    val service = createService()
    val scheduler = new EventScheduler(Stage(Sequential)(service.legalHoldEventStage))
    new EventPipelineImpl(Vector.empty, scheduler.enqueue)
  }

  // Tests

  feature("Is legal hold active for self user") {

    scenario("with a legal hold device") {
      // Given
      val service = createService()
      mockUserDevices(selfUserId, Seq(DeviceClass.Phone, DeviceClass.LegalHold))

      // When
      val actualResult = result(service.isLegalHoldActiveForSelfUser.future)

      // Then
      actualResult shouldBe true
    }

    scenario("without a legal hold device") {
      // Given
      val service = createService()
      mockUserDevices(selfUserId, Seq(DeviceClass.Phone))

      // When
      val actualResult = result(service.isLegalHoldActiveForSelfUser.future)

      // Then
      actualResult shouldBe false
    }
  }

  feature("legal hold request sync") {

    scenario("stores the request if exists") {
      //Given
      val service = createService()

      //When
      await(service.onLegalHoldRequestSynced(Some(legalHoldRequest)))

      //Then
      val storedLegalHoldRequest = result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply())
      storedLegalHoldRequest.isDefined shouldBe true
      storedLegalHoldRequest.get.clientId shouldEqual legalHoldRequest.clientId
      storedLegalHoldRequest.get.lastPreKey.id shouldEqual legalHoldRequest.lastPreKey.id
      storedLegalHoldRequest.get.lastPreKey.data shouldEqual legalHoldRequest.lastPreKey.data
    }

    scenario("deletes request and notifies active state if no request exists but LH is active") {
      //Given
      val service = createService()
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))
      userPrefs.setValue(UserPreferences.LegalHoldDisclosureType, None)
      mockUserDevices(selfUserId, Seq(DeviceClass.Phone, DeviceClass.LegalHold))

      //When
      await(service.onLegalHoldRequestSynced(None))

      //Then
      val storedLegalHoldRequest = result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply())
      storedLegalHoldRequest.isDefined shouldBe false
      val storedDisclosureType = result(userPrefs.preference(UserPreferences.LegalHoldDisclosureType).apply())
      storedDisclosureType.isDefined shouldBe true
      storedDisclosureType.get.value shouldBe LegalHoldStatus.Enabled.value
    }

    scenario("deletes request and does not change state if no request exists and LH is  not active") {
      //Given
      val service = createService()
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))
      userPrefs.setValue(UserPreferences.LegalHoldDisclosureType, None)
      mockUserDevices(selfUserId, Seq(DeviceClass.Phone))

      //When
      await(service.onLegalHoldRequestSynced(None))

      //Then
      val storedLegalHoldRequest = result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply())
      storedLegalHoldRequest.isDefined shouldBe false
      val storedDisclosureType = result(userPrefs.preference(UserPreferences.LegalHoldDisclosureType).apply())
      storedDisclosureType.isDefined shouldBe false
    }
  }

  feature("Is legal hold active for a conversation") {

    scenario("for a conversation with enabled legal hold status") {
      // Given
      val service = createService()
      val convId = ConvId("conv1")
      mockConversation(convId, LegalHoldStatus.Enabled)

      // When
      val actualResult = result(service.isLegalHoldActive(convId).future)

      // Then
      actualResult shouldBe true
    }

    scenario("for a conversation with disabled legal hold status") {
      // Given
      val service = createService()
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
      val service = createService()
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
      val service = createService()
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
      val service = createService()
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
      val service = createService()
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

      (userService.syncClients(_: UserId))
        .expects(*)
        .anyNumberOfTimes()
        .returning(Future.successful(SyncId("syncId")))

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

      (userService.syncClients(_: UserId))
        .expects(*)
        .anyNumberOfTimes()
        .returning(Future.successful(SyncId("syncId")))

      // When
      result(pipeline.apply(Seq(LegalHoldDisableEvent(UserId("someOtherUser")))))

      // Then
      result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply()).isEmpty shouldBe false
    }

    scenario("it syncs clients when legal hold is enabled for another user") {
      // Given
      val otherUserId = UserId("someOtherUser")
      val pipeline = createEventPipeline()

      // Expectation
      (userService.syncClients(_: UserId))
          .expects(otherUserId)
          .once()
          .returning(Future.successful(SyncId("syncId")))

      // When
      result(pipeline.apply(Seq(LegalHoldEnableEvent(otherUserId))))
    }

    scenario("it syncs clients when legal hold is disabled for another user") {
      // Given
      val otherUserId = UserId("someOtherUser")
      val pipeline = createEventPipeline()

      // Expectation
      (userService.syncClients(_: UserId))
        .expects(otherUserId)
        .once()
        .returning(Future.successful(SyncId("syncId")))

      // When
      result(pipeline.apply(Seq(LegalHoldDisableEvent(otherUserId))))
    }

  }

  feature("Approve legal hold request") {

    scenario("It creates a client and and approves the request") {
      // Given
      val service = createService()
      userPrefs.setValue(UserPreferences.LegalHoldRequest, Some(legalHoldRequest))

      mockClientAndSessionCreation()

      // Approve the request.
      (apiClient.approveRequest _)
        .expects(teamId, selfUserId, Some("password"))
        .once()
        .returning(CancellableFuture.successful(Right({})))

      val legalHoldClient = Client(
        legalHoldRequest.clientId,
        deviceClass = DeviceClass.LegalHold,
        isTemporary = true
      )

      (clientsService.getClient _)
        .expects(selfUserId, legalHoldClient.id)
        .once()
        .returning(Future.successful(Some(legalHoldClient)))

      val permanentLegalHoldClient = legalHoldClient.copy(isTemporary = false)
      val userClients = UserClients(selfUserId, Map(permanentLegalHoldClient.id -> permanentLegalHoldClient))

      (clientsService.updateUserClients(_: UserId, _: Seq[Client], _: Boolean))
        .expects(selfUserId, Seq(permanentLegalHoldClient), false)
        .once()
        .returning(Future.successful(userClients))

      // When
      val actualResult = result(service.approveRequest(legalHoldRequest, Some("password")))

      // Then
      actualResult.isRight shouldBe true
      result(userPrefs.preference(UserPreferences.LegalHoldRequest).apply()) shouldBe None
    }

    scenario("It deletes the client if approval failed") {
      // Given
      val service = createService()
      val client = mockClientAndSessionCreation()

      // Approve the request.
      (apiClient.approveRequest _)
        .expects(teamId, selfUserId, Some("password"))
        .once()
        .returning(CancellableFuture.successful(Left(ErrorResponse(400, "", "access-denied"))))

      // Delete client.
      (clientsService.removeClients _ )
        .expects(selfUserId, Set(client.id))
        .once()
        // We don't care about the return type.
        .returning(Future.successful(None))

      // Delete session.
      (cryptoSessionService.deleteSession _)
        .expects(SessionId(selfUserId, selfDomain, client.id))
        .once()
        .returning(Future.successful({}))

      // When
      val actualResult = result(service.approveRequest(legalHoldRequest, Some("password")))

      // Then
      actualResult shouldBe Left(LegalHoldError.InvalidPassword)
    }

    def mockClientAndSessionCreation(): Client = {
      val client = Client(legalHoldRequest.clientId, "", deviceClass = DeviceClass.LegalHold, isTemporary = true)

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
        .expects(SessionId(selfUserId, selfDomain, client.id), legalHoldRequest.lastPreKey)
        .once()
        // To make testing simpler, just return none since
        // we don't actually need to use the crypto session.
        .returning(Future.successful(None))

      client
    }

  }

  feature("Update legal hold status") {

    scenario("from disabled to enabled") {
      // Expectations
      val convSignal = setUpExpectationsForConversationUpdate(
        legalHoldStatus = ConversationData.LegalHoldStatus.Disabled,
        existsLegalHoldDevice = true
      )

      val done = Promise[Unit]()

      (messagesService.addLegalHoldEnabledMessage _)
          .expects(convSignal.currentValue.get.id, None)
          .once()
        .onCall { (_, _) =>
          done.success(())
          // Note: return value is not important.
          Future.successful(None)
        }

      // When (the service is initialized, it updates the legal hold status)
      createService(setUpInitExpectations = false)

      // Then
      result(convSignal.filter(_.isUnderLegalHold).head)
      result(done.future)
    }

    scenario("from enabled to disabled") {
      // Expectations
      val convSignal = setUpExpectationsForConversationUpdate(
        legalHoldStatus = ConversationData.LegalHoldStatus.Enabled,
        existsLegalHoldDevice = false
      )

      val done = Promise[Unit]()

      (messagesService.addLegalHoldDisabledMessage _)
        .expects(convSignal.currentValue.get.id, None)
        .once()
        .onCall { (_, _) =>
          done.success(())
          // Note: return value is not important.
          Future.successful(None)
        }

      // When (the service is initialized, it updates the legal hold status)
      createService(setUpInitExpectations = false)

      // Then
      result(convSignal.filter(!_.isUnderLegalHold).head)
      result(done.future)
    }

    def setUpExpectationsForConversationUpdate(legalHoldStatus: ConversationData.LegalHoldStatus,
                                               existsLegalHoldDevice: Boolean): Signal[ConversationData] = {
      import DeviceClass._

      // Given
      val user1 = UserId("user1")
      val user2 = UserId("user2")
      val client1 = Client(ClientId("client1"), "", deviceClass = Phone)
      val client2 = Client(ClientId("client2"), "", deviceClass = if (existsLegalHoldDevice) LegalHold else Phone)
      val convId = ConvId("conv1")
      val convData = ConversationData(convId, legalHoldStatus = legalHoldStatus)
      val convSignal = Signal(convData)

      // Get the list of all conversations
      (convsStorage.contents _)
        .expects()
        .once()
        .returning(Signal.const(Map(convId -> convData)))

      // Get the active users of the conversations.
      (membersStorage.activeMembers _)
        .expects(convId)
        .once()
        .returning(Signal.const(Set(user1, user2)))

      // Get the clients for user 1
      (clientsStorage.optSignal _)
        .expects(user1)
        .once()
        .returning(Signal.const(Some(UserClients(user1, Map(client1.id -> client1)))))

      // Get the clients for user 2
      (clientsStorage.optSignal _)
        .expects(user2)
        .once()
        .returning(Signal.const(Some(UserClients(user2, Map(client2.id -> client2)))))

      // Finally update the conversation.
      (convsStorage.updateAll2 _)
        .expects(*, *)
        .once()
        .onCall { (_, updater) =>
          val updatedConv = updater(convData)
          convSignal ! updatedConv
          Future.successful(Seq((convData, updatedConv)))
        }

      convSignal
    }

  }

  feature("it calculates correct legal hold status") {

    scenario("existing status is disabled") {
      assert(existingStatus = Disabled, detectedLegalHoldDevice = true, expectation = Enabled)
      assert(existingStatus = Disabled, detectedLegalHoldDevice = false, expectation = Disabled)
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
      val conv = ConversationData(ConvId("convId"), RConvId("convId"), legalHoldStatus = Disabled)
      val message = createMessage(Messages.LegalHoldStatus.ENABLED)
      val time = RemoteInstant(Instant.now())
      val event = createEvent(conv.remoteId, message, time)

      // Expectations
      (convsStorage.getByRemoteId _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(Some(conv)))

      var updatedStatus: LegalHoldStatus = conv.legalHoldStatus

      (convsStorage.update _)
        .expects(conv.id, *)
        .once()
        .onCall { (_, updater) =>
          val updatedConv = updater(conv)
          updatedStatus = updatedConv.legalHoldStatus
          Future.successful(Some(conv, updatedConv))
        }

      // Note: the return value is not important.
      (messagesService.addLegalHoldEnabledMessage _)
        .expects(conv.id, Some(time - 5.millis))
        .once()
        .returning(Future.successful(None))

      (sync.syncClientsForLegalHold _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(SyncId("syncId")))

      // When
      val service = createService()
      val pipeline = createMessageEventPipeline(service)
      result(pipeline.apply(Seq(event)))

      // Then
      updatedStatus shouldEqual LegalHoldStatus.Enabled
    }

    scenario("it triggers client sync if it discovers legal hold to be disabled") {
      // Given
      val conv = ConversationData(ConvId("convId"), RConvId("convId"), legalHoldStatus = Enabled)
      val message = createMessage(Messages.LegalHoldStatus.DISABLED)
      val time = RemoteInstant(Instant.now())
      val event = createEvent(conv.remoteId, message, time)

      // Expectations
      (convsStorage.getByRemoteId _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(Some(conv)))

      var updatedStatus: LegalHoldStatus = conv.legalHoldStatus

      (convsStorage.update _)
        .expects(conv.id, *)
        .once()
        .onCall { (_, updater) =>
          val updatedConv = updater(conv)
          updatedStatus = updatedConv.legalHoldStatus
          Future.successful(Some(conv, updatedConv))
        }

      // Note: the return value is not important.
      (messagesService.addLegalHoldDisabledMessage _)
        .expects(conv.id, Some(time - 5.millis))
        .once()
        .returning(Future.successful(None))

      (sync.syncClientsForLegalHold _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(SyncId("syncId")))

      // When
      val service = createService()
      val pipeline = createMessageEventPipeline(service)
      result(pipeline.apply(Seq(event)))

      // Then
      updatedStatus shouldEqual LegalHoldStatus.Disabled
    }

    scenario("it does not trigger client sync if current status and hint are both enabled") {
      // Given
      val conv = ConversationData(ConvId("convId"), RConvId("convId"), legalHoldStatus = Enabled)
      val message = createMessage(Messages.LegalHoldStatus.ENABLED)
      val event = createEvent(conv.remoteId, message)

      // Expectations
      (convsStorage.getByRemoteId _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(Some(conv)))

      (convsStorage.update _)
        .expects(conv.id, *)
        .never()

      (sync.syncClientsForLegalHold _)
        .expects(conv.remoteId)
        .never()

      // When
      val service = createService()
      val pipeline = createMessageEventPipeline(service)
      result(pipeline.apply(Seq(event)))
    }

    scenario("it does not trigger client sync if current status and hint are both disabled") {
      // Given
      val conv = ConversationData(ConvId("convId"), RConvId("convId"), legalHoldStatus = Disabled)
      val message = createMessage(Messages.LegalHoldStatus.DISABLED)
      val event = createEvent(conv.remoteId, message)

      // Expectations
      (convsStorage.getByRemoteId _)
        .expects(conv.remoteId)
        .once()
        .returning(Future.successful(Some(conv)))

      (convsStorage.update _)
        .expects(conv.id, *)
        .never()

      (sync.syncClientsForLegalHold _)
        .expects(conv.remoteId)
        .never()

      // When
      val service = createService()
      val pipeline = createMessageEventPipeline(service)
      result(pipeline.apply(Seq(event)))
    }

    def createMessageEventPipeline(service: LegalHoldServiceImpl): EventPipeline = {
      val scheduler = new EventScheduler(Stage(Sequential)(service.messageEventStage))
      new EventPipelineImpl(Vector.empty, scheduler.enqueue)
    }

    def createMessage(status: Messages.LegalHoldStatus): GenericMessage =
      GenericMessage(Uid("messageId"), None, Text("Hello!", legalHoldStatus = status))

    def createEvent(convId: RConvId,
                    message: GenericMessage,
                    time: RemoteInstant = RemoteInstant(Instant.now())): GenericMessageEvent =
      GenericMessageEvent(
        convId,
        Domain.Empty,
        time,
        UserId("senderId"),
        Domain.Empty,
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
