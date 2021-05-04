package com.waz.service


import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage}
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.model.{ConvId, ConversationData, Messages, RConvId, UserId}
import com.waz.model.ConversationData.LegalHoldStatus
import com.waz.model.ConversationData.LegalHoldStatus._
import com.waz.specs.AndroidFreeSpec
import com.wire.signals.EventStream

import scala.concurrent.Future

class LegalHoldStatusUpdaterSpec extends AndroidFreeSpec {

  // Mocks

  private val clients = mock[OtrClientsStorage]
  private val convs = mock[ConversationStorage]
  private val members = mock[MembersStorage]
  private val userService = mock[UserService]

  // Set up

  private var statusUpdater: LegalHoldStatusUpdaterImpl = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    // The status updater reacts to these event streams so we need to mock
    // them, but we return empty streams and instead test the status updater
    // methods directly.

    (clients.onChanged _)
      .expects()
      .once()
      .returning(EventStream())

    (members.onAdded _)
      .expects()
      .once()
      .returning(EventStream())

    (members.onDeleted _)
      .expects()
      .once()
      .returning(EventStream())

    statusUpdater = new LegalHoldStatusUpdaterImpl(clients, convs, members, userService)
  }

  // Tests

  feature("it updates the conversation") {
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
      result(statusUpdater.updateLegalHoldStatus(Seq(convId)))
    }

    scenario("on clients changed") {
      // Given
      val userClients = UserClients(user1, Map(client1.id -> client1))

      // Expectations
      (members.getActiveConvs _)
          .expects(user1)
          .once()
          .returning(Future.successful(Seq(convId)))

      setUpExpectationsForConversationUpdate()

      // When
      result(statusUpdater.onClientsChanged(Seq(userClients)))
    }

    def setUpExpectationsForConversationUpdate(): Unit = {
      // Get the active users of the conversations.
      (members.getActiveUsers _)
        .expects(convId)
        .once()
        .returning(Future.successful(Seq(user1, user2)))

      // And all of their clients.
      (clients.getClients _)
        .expects(user1)
        .once()
        .returning(Future.successful(Seq(client1)))

      (clients.getClients _)
        .expects(user2)
        .once()
        .returning(Future.successful(Seq(client2)))

      // Finally update the conversation.
      (convs.updateAll2 _)
        .expects(Seq(convId), *)
        .once()
        // The return value is not important.
        .returning(Future.successful(Seq()))
    }

  }

  feature("it calculates status when") {

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

    scenario("it triggers client sync if status differ") {
      // Given
      val remoteConvId = RConvId("convId")
      val convId = ConvId("convId")
      val conv = ConversationData(convId, remoteConvId, legalHoldStatus = Disabled)
      val messageStatus = Messages.LegalHoldStatus.ENABLED

      // Expectations
      (convs.getByRemoteId _)
          .expects(remoteConvId)
          .once()
          .returning(Future.successful(Some(conv)))

      // Note: we don't care about the return value
      (convs.update _)
          .expects(convId, *)
          .once()
          .returning(Future.successful(None))

      (userService.syncClients(_ : ConvId))
          .expects(convId)
          .once()
          .returning(Future.successful(()))

      // When
      result(statusUpdater.updateStatusFromMessageHint(remoteConvId, messageStatus))
    }

    scenario("it does not trigger client sync if status are equal") {
      // Given
      val remoteConvId = RConvId("convId")
      val convId = ConvId("convId")
      val conv = ConversationData(convId, remoteConvId, legalHoldStatus = Enabled)
      val messageStatus = Messages.LegalHoldStatus.ENABLED

      // Expectations
      (convs.getByRemoteId _)
        .expects(remoteConvId)
        .once()
        .returning(Future.successful(Some(conv)))

      // When
      result(statusUpdater.updateStatusFromMessageHint(remoteConvId, messageStatus))
    }
  }
}
