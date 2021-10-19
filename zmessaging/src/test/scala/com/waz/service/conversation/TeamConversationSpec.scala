/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.conversation

import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.content.{ButtonsStorage, ConversationStorage, MembersStorage, MessagesStorage, MsgDeletionStorage, UsersStorage}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.ConversationData.ConversationType._
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.{ConversationMemberData, _}
import com.waz.service.assets.{AssetService, UriHelper}
import com.waz.service.{ErrorsService, NetworkModeService, PropertiesService, SearchKey, UserService}
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient
import com.waz.sync.{SyncRequestService, SyncResult, SyncServiceHandle}
import com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}
import com.waz.zms.BuildConfig

import scala.concurrent.Future

class TeamConversationSpec extends AndroidFreeSpec {
  import ConversationRole._

  val selfId          = UserId()
  val domain          = if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("chala.wire.link") else Domain.Empty
  val team            = Some(TeamId("team"))
  val selfUser        = UserData(selfId, domain, team, Name("self"), searchKey = SearchKey.simple("self"))
  val users           = mock[UserService]
  val members         = mock[MembersStorage]
  val convsContent    = mock[ConversationsContentUpdater]
  val convsStorage    = mock[ConversationStorage]
  val convsService    = mock[ConversationsService]
  val sync            = mock[SyncServiceHandle]
  val requests        = mock[SyncRequestService]
  val messages        = mock[MessagesService]
  val messagesStorage = mock[MessagesStorage]
  val deletions       = mock[MsgDeletionStorage]
  val assetService    = mock[AssetService]
  val network         = mock[NetworkModeService]
  val properties      = mock[PropertiesService]
  val buttons         = mock[ButtonsStorage]
  val client          = mock[ConversationsClient]
  val errors          = mock[ErrorsService]
  val uriHelper       = mock[UriHelper]

  val prefs       = new TestGlobalPreferences()
  val userPrefs   = new TestUserPreferences()

  def initService: ConversationsUiService = {
    val msgContent = new MessagesContentUpdater(messagesStorage, convsStorage, deletions, buttons, prefs, userPrefs)
    new ConversationsUiServiceImpl(selfId, team, assetService, users, messages, messagesStorage,
      msgContent, members, convsContent, convsStorage, network, convsService, sync, requests, client,
      accounts, tracking, errors, uriHelper, properties)
  }

  feature("Creating team conversations") {

    scenario("Create 1:1 conversation within a team with existing 1:1 conversation between the two members should return existing conversation") {
      val otherUserId = UserId("otherUser")
      val otherUser = UserData(otherUserId, domain, team, Name("other"), searchKey = SearchKey.simple("other"))

      val existingConv = ConversationData(creator = selfId, convType = Group, team = team)

      (users.findUser _).expects(otherUserId).once().returning(Future.successful(Some(otherUser)))

      (members.getByUsers _).expects(Set(otherUserId)).once().returning(Future.successful(IndexedSeq(
        ConversationMemberData(otherUserId, existingConv.id, AdminRole)
      )))

      (members.getByConvs _).expects(Set(existingConv.id)).once().returning(Future.successful(IndexedSeq(
        ConversationMemberData(selfId,        existingConv.id, AdminRole),
        ConversationMemberData(otherUserId, existingConv.id, AdminRole)
      )))

      (convsStorage.getAll _).expects(Seq(existingConv.id)).once().returning(Future.successful(Seq(Some(existingConv))))

      result(initService.getOrCreateOneToOneConversation(otherUserId)) shouldEqual existingConv
    }

    scenario("Existing 1:1 conversation between two team members with NAME should not be returned") {
      val otherUserId = UserId("otherUser")
      val otherUser = UserData(otherUserId, domain, team, Name("other"), searchKey = SearchKey.simple("other"))

      val name = Some(Name("Conv Name"))
      val existingConv = ConversationData(creator = selfId, name = name, convType = Group, team = team)

      (users.findUser _).expects(otherUserId).once().returning(Future.successful(Some(otherUser)))

      (members.getByUsers _).expects(Set(otherUserId)).anyNumberOfTimes().returning(Future.successful(IndexedSeq(
        ConversationMemberData(otherUserId, existingConv.id, AdminRole)
      )))

      (members.getByConvs _).expects(Set(existingConv.id)).once().returning(Future.successful(IndexedSeq(
        ConversationMemberData(selfId,      existingConv.id, AdminRole),
        ConversationMemberData(otherUserId, existingConv.id, AdminRole)
      )))

      (convsStorage.getAll _).expects(Seq(existingConv.id)).once().returning(Future.successful(Seq(Some(existingConv))))
      (convsContent.createConversation _)
        .expects(*, *, Group, selfId, ConversationRole.AdminRole, None, false, Set(Access.INVITE, Access.CODE), AccessRole.NON_ACTIVATED, 0).once().onCall {
        (conv: ConvId, r: RConvId, tpe: ConversationType, cr: UserId, _: ConversationRole, n: Option[Name], hid: Boolean, ac: Set[Access], ar: AccessRole, rr: Int) =>
          Future.successful(ConversationData(conv, r, n, cr, tpe, team, hidden = hid, access = ac, accessRole = Some(ar), receiptMode = Some(rr)))
      }
      (messages.addConversationStartMessage _).expects(*, selfId, *, None, *, None).once().returning(Future.successful({}))
      (sync.postConversation(_: ConvId, _: Option[UserId], _: Option[Name], _: Option[TeamId],
        _: Set[Access], _: AccessRole, _: Option[Int], _: ConversationRole))
        .expects(*, *, None, team, Set(Access.INVITE, Access.CODE), AccessRole.NON_ACTIVATED, Some(0), *)
        .once()
        .returning(Future.successful(SyncId()))
      (requests.await(_: SyncId)).expects(*).anyNumberOfTimes().returning(Future.successful(SyncResult.Success))
      (members.isActiveMember _).expects(*, *).anyNumberOfTimes().returning(Future.successful(true))
      (convsService.isGroupConversation _).expects(*).anyNumberOfTimes().returning(Future.successful(true))

      (convsService.generateTempConversationId _).expects(*).anyNumberOfTimes().returning(RConvId())

      val conv = result(initService.getOrCreateOneToOneConversation(otherUserId))
      conv shouldNot equal(existingConv)
    }
  }

  feature("Conversations with guests") {

    //TODO under what circumstances is the user connection status "Ignored"? What happens if you're just unconnected with that person?
    scenario("Create 1:1 conversation with a non-team member should create a real 1:1 conversation") {
      val otherUserId = UserId("otherUser")
      val otherUser = UserData(otherUserId, domain, Some(TeamId("different_team")), Name("other"), searchKey = SearchKey.simple("other"), connection = ConnectionStatus.Ignored)

      (users.findUser _).expects(otherUserId).twice().returning(Future.successful(Some(otherUser)))

      (convsContent.convById _).expects(ConvId("otherUser")).returning(Future.successful(None))
      (convsContent.createConversation _)
        .expects(ConvId("otherUser"), *, Incoming, otherUserId, ConversationRole.AdminRole, None, true, Set(Access.PRIVATE), AccessRole.PRIVATE, 0).once().onCall {
        (conv: ConvId, r: RConvId, tpe: ConversationType, cr: UserId, _: ConversationRole, n: Option[Name], hid: Boolean, ac: Set[Access], ar: AccessRole, rr: Int) =>
          Future.successful(ConversationData(conv, r, n, cr, tpe, team, hidden = hid, access = ac, accessRole = Some(ar), receiptMode = Some(rr)))
      }

      (messages.addMemberJoinMessage _).expects(ConvId("otherUser"), otherUserId, Set(selfId), true, false).once().returning(Future.successful(null))

      val conv = result(initService.getOrCreateOneToOneConversation(otherUserId))
      conv.id shouldEqual ConvId("otherUser")
    }
  }
}
