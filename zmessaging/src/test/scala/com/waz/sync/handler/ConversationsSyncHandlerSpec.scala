package com.waz.sync.handler

import com.waz.api.ErrorType
import com.waz.api.IConversation.AccessRole
import com.waz.api.impl.ErrorResponse
import com.waz.content.{ConversationStorage, MembersStorage, MessagesStorage}
import com.waz.model._
import com.waz.service.conversation.{ConversationOrderEventsService, ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.MessagesService
import com.waz.service.{ConversationRolesService, ErrorsService, GenericMessageService, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client.ConversationsClient.ConversationResponse.ConversationsResult
import com.waz.sync.client.{ConversationsClient, ErrorOrResponse}
import com.wire.signals.{CancellableFuture, Signal}
import com.waz.zms.BuildConfig

import scala.concurrent.Future

class ConversationsSyncHandlerSpec extends AndroidFreeSpec {

  private val self = UserData("self")
  private val teamId = TeamId()
  private val userService      = mock[UserService]
  private val messagesStorage = mock[MessagesStorage]
  private val messagesService = mock[MessagesService]
  private val convService = mock[ConversationsService]
  private val convs = mock[ConversationsContentUpdater]
  private val convEvents = mock[ConversationOrderEventsService]
  private val convStorage = mock[ConversationStorage]
  private val errorsService = mock[ErrorsService]
  private val conversationsClient = mock[ConversationsClient]
  private val genericMessages = mock[GenericMessageService]
  private val rolesService = mock[ConversationRolesService]
  private val membersStorage = mock[MembersStorage]

  private def createHandler: ConversationsSyncHandler = new ConversationsSyncHandler(
    self.id, Some(teamId), userService, messagesStorage, messagesService,
    convService, convs, convEvents, convStorage, errorsService,
    conversationsClient, genericMessages, rolesService, membersStorage
  )

  private def toConversationResponse(conv: ConversationData) =
    ConversationResponse(
      conv.remoteId, conv.domain, conv.name, conv.creator, conv.convType, conv.team,
      conv.muted, conv.muteTime, conv.archived, conv.archiveTime,
      conv.access, conv.accessRole, conv.link, None, Map.empty, conv.receiptMode
    )

  scenario("Sync non-qualified conversations") {
    val conv1 = ConversationData(team = Some(teamId), creator = self.id)
    val conv2 = ConversationData(team = Some(teamId), creator = self.id)
    val conv3 = ConversationData(team = Some(teamId), creator = self.id)

    val convMap = Seq(conv1, conv2, conv3).toIdMap

    val resp1 = toConversationResponse(conv1)
    val resp2 = toConversationResponse(conv2)
    val resp3 = toConversationResponse(conv3)
    val resps = Seq(resp1, resp2, resp3)

    val backendResponse: ErrorOrResponse[Seq[ConversationResponse]] =
      CancellableFuture.successful { Right(resps) }

    (convStorage.getAll _)
      .expects(convMap.keySet).once().returning(Future.successful(convMap.values.map(Option(_)).toSeq))
    (conversationsClient.loadConversations(_: Set[RConvId]))
      .expects(convMap.values.map(_.remoteId).toSet).once().returning(backendResponse)
    (conversationsClient.loadQualifiedConversations(_: Set[RConvQualifiedId])).expects(*).never()

    (rolesService.defaultRoles _).expects().anyNumberOfTimes().returning(Signal.const(Set.empty[ConversationRole]))
    (conversationsClient.loadConversationRoles _)
      .expects(*, *).anyNumberOfTimes().returning(Future.successful(Map.empty[RConvId, Set[ConversationRole]]))
    (convService.updateConversationsWithDeviceStartMessage _).expects(resps, *).once().returning(Future.successful(()))

    val handler = createHandler
    result(handler.syncConversations(convMap.keySet))
  }

  scenario("Sync qualified conversations") {
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      val conv1 = ConversationData(team = Some(teamId), creator = self.id, domain = Some("anta"))
      val conv2 = ConversationData(team = Some(teamId), creator = self.id, domain = Some("anta"))
      val conv3 = ConversationData(team = Some(teamId), creator = self.id, domain = Some("anta"))

      val convMap = Seq(conv1, conv2, conv3).toIdMap

      val resp1 = toConversationResponse(conv1)
      val resp2 = toConversationResponse(conv2)
      val resp3 = toConversationResponse(conv3)
      val resps = Seq(resp1, resp2, resp3)

      val backendResponse: ErrorOrResponse[Seq[ConversationResponse]] =
        CancellableFuture.successful {
          Right(resps)
        }

      (convStorage.getAll _)
        .expects(convMap.keySet).once().returning(Future.successful(convMap.values.map(Option(_)).toSeq))
      (conversationsClient.loadQualifiedConversations(_: Set[RConvQualifiedId]))
        .expects(convMap.values.flatMap(_.qualifiedId).toSet).once().returning(backendResponse)
      (conversationsClient.loadConversations(_: Set[RConvId])).expects(*).never()

      (rolesService.defaultRoles _).expects().anyNumberOfTimes().returning(Signal.const(Set.empty[ConversationRole]))
      (conversationsClient.loadConversationRoles _)
        .expects(*, *).anyNumberOfTimes().returning(Future.successful(Map.empty[RConvId, Set[ConversationRole]]))
      (convService.updateConversationsWithDeviceStartMessage _).expects(resps, *).once().returning(Future.successful(()))

      val handler = createHandler
      result(handler.syncConversations(convMap.keySet))
    }
  }

  scenario("Sync part qualified part non-qualified conversations") {
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      val conv1 = ConversationData(team = Some(teamId), creator = self.id, domain = Some("anta"))
      val conv2 = ConversationData(team = Some(teamId), creator = self.id, domain = Some("anta"))
      val conv3 = ConversationData(team = Some(teamId), creator = self.id)

      val convMap = Seq(conv1, conv2, conv3).toIdMap

      val resp1 = toConversationResponse(conv1)
      val resp2 = toConversationResponse(conv2)
      val resp3 = toConversationResponse(conv3)
      val resps = Seq(resp1, resp2, resp3)

      val qBackendResponse: ErrorOrResponse[Seq[ConversationResponse]] =
        CancellableFuture.successful {
          Right(Seq(resp1, resp2))
        }
      val backendResponse: ErrorOrResponse[Seq[ConversationResponse]] =
        CancellableFuture.successful {
          Right(Seq(resp3))
        }

      (convStorage.getAll _)
        .expects(convMap.keySet).once().returning(Future.successful(convMap.values.map(Option(_)).toSeq))
      (conversationsClient.loadQualifiedConversations(_: Set[RConvQualifiedId]))
        .expects(Seq(conv1, conv2).flatMap(_.qualifiedId).toSet).once().returning(qBackendResponse)
      (conversationsClient.loadConversations(_: Set[RConvId]))
        .expects(Set(conv3.remoteId)).once().returning(backendResponse)

      (rolesService.defaultRoles _).expects().anyNumberOfTimes().returning(Signal.const(Set.empty[ConversationRole]))
      (conversationsClient.loadConversationRoles _)
        .expects(*, *).anyNumberOfTimes().returning(Future.successful(Map.empty[RConvId, Set[ConversationRole]]))
      (convService.updateConversationsWithDeviceStartMessage _).expects(resps, *).once().returning(Future.successful(()))

      val handler = createHandler
      result(handler.syncConversations(convMap.keySet))
    }
  }

  scenario("When syncing conversations, given local convs that are absent from the backend, remove the missing local convs") {
    val conv1 = ConversationData(team = Some(teamId), creator = self.id)
    val conv2 = ConversationData(team = Some(teamId), creator = self.id)
    val conv3 = ConversationData(team = Some(teamId), creator = self.id)

    val resp1 = toConversationResponse(conv1)
    val resp2 = toConversationResponse(conv2)

    val backendResponse: ErrorOrResponse[ConversationsResult] =
      CancellableFuture.successful { Right(ConversationsResult(Seq(resp1, resp2), hasMore = false)) }
    val storageResponse =
      Future.successful(Set(conv1.remoteId, conv2.remoteId, conv3.remoteId))

    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      (conversationsClient.loadQualifiedConversations(_: Option[RConvQualifiedId], _: Int))
        .expects(*, *).anyNumberOfTimes().returning(backendResponse)
    } else {
      (conversationsClient.loadConversations(_: Option[RConvId], _: Int))
        .expects(*, *).anyNumberOfTimes().returning(backendResponse)
    }

    (convService.remoteIds _).expects().anyNumberOfTimes().returning(storageResponse)

    (rolesService.defaultRoles _).expects().anyNumberOfTimes().returning(Signal.const(Set.empty[ConversationRole]))
    (conversationsClient.loadConversationRoles _).expects(*,*).anyNumberOfTimes().returning(Future.successful(Map.empty[RConvId, Set[ConversationRole]]))
    (convService.updateConversationsWithDeviceStartMessage _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

    (convService.deleteConversation _).expects(conv3.remoteId).once().returning(Future.successful(()))
    val handler = createHandler
    result(handler.syncConversations())
  }

  scenario("It reports missing legal hold consent error when adding participants") {
    // Given
    val handler = createHandler
    val convId = ConvId("convId")
    val members = Set(UserId("userId"))
    val errorResponse = ErrorResponse(412, "", "missing-legalhold-consent")

    // Mock
    (convs.convById _)
      .expects(convId)
      .once()
      .returning(Future.successful(Some(ConversationData(convId))))

    (conversationsClient.postMemberJoin _)
        .expects(*, *, *)
        .once()
        .returning(CancellableFuture.successful(Left(errorResponse)))

    // Expectation
    val errorType = ErrorType.CANNOT_ADD_PARTICIPANT_WITH_MISSING_LEGAL_HOLD_CONSENT
    (convService.onMemberAddFailed _)
        .expects(convId, members, Some(errorType), errorResponse)
        .once()
        .returning(Future.successful(()))

    // When
    result(handler.postConversationMemberJoin(convId, members, ConversationRole.MemberRole))
  }

  scenario("It reports missing legal hold consent error when adding qualified participants") {
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      // Given
      val handler = createHandler
      val convId = ConvId("convId")
      val members = Set(QualifiedId(UserId("userId"), "chala.wire.link"))
      val errorResponse = ErrorResponse(412, "", "missing-legalhold-consent")

      // Mock
      (convs.convById _)
        .expects(convId)
        .once()
        .returning(Future.successful(Some(ConversationData(convId))))

      (conversationsClient.postQualifiedMemberJoin _)
        .expects(*, *, *)
        .once()
        .returning(CancellableFuture.successful(Left(errorResponse)))

      // Expectation
      val errorType = ErrorType.CANNOT_ADD_PARTICIPANT_WITH_MISSING_LEGAL_HOLD_CONSENT
      (convService.onMemberAddFailed _)
        .expects(convId, members.map(_.id), Some(errorType), errorResponse)
        .once()
        .returning(Future.successful(()))

      // When
      result(handler.postQualifiedConversationMemberJoin(convId, members, ConversationRole.MemberRole))
    }
  }

  scenario("It reports missing legal hold consent error when creating conversation") {
    // Given
    val handler = createHandler
    val convId = ConvId("convId")
    val errorResponse = ErrorResponse(412, "", "missing-legalhold-consent")

    // Mock
    (conversationsClient.postConversation _)
      .expects(*)
      .once()
      .returning(CancellableFuture.successful(Left(errorResponse)))

    // Expectation
    val errorType = ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_USER_MISSING_LEGAL_HOLD_CONSENT

    (errorsService.addErrorWhenActive _)
      .expects(where { data: ErrorData =>
        data.errType == errorType &&
        data.responseCode == 412 &&
        data.responseLabel == "missing-legalhold-consent" &&
        data.convId.contains(convId)
      })
      .once()
      .returning(Future.successful(()))

    // When (arguments are irrelevant)
    result(handler.postConversation(
      convId,
      Set(UserId("userId")),
      None,
      None,
      Set.empty,
      AccessRole.TEAM,
      None,
      ConversationRole.MemberRole
    ))
  }
}
