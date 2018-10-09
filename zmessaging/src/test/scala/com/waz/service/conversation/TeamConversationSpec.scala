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
import com.waz.content.{ConversationStorage, MembersStorage, UsersStorage}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.ConversationData.ConversationType._
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.{ConversationMemberData, _}
import com.waz.service.SearchKey
import com.waz.service.messages.MessagesService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle

import scala.concurrent.Future

class TeamConversationSpec extends AndroidFreeSpec {

  val self         = UserId()
  val team         = Some(TeamId("team"))
  val userStorage  = mock[UsersStorage]
  val members      = mock[MembersStorage]
  val convsContent = mock[ConversationsContentUpdater]
  val convsStorage = mock[ConversationStorage]
  val sync         = mock[SyncServiceHandle]
  val messages     = mock[MessagesService]

  feature("Creating team conversations") {

    scenario("Create 1:1 conversation within a team with existing 1:1 conversation between the two members should return existing conversation") {
      val otherUserId = UserId("otherUser")
      val otherUser = UserData(otherUserId, team, "other", searchKey = SearchKey.simple("other"))

      val existingConv = ConversationData(creator = self, convType = Group, team = team)

      (userStorage.get _).expects(otherUserId).once().returning(Future.successful(Some(otherUser)))

      (members.getByUsers _).expects(Set(otherUserId)).once().returning(Future.successful(IndexedSeq(
        ConversationMemberData(otherUserId, existingConv.id)
      )))

      (members.getByConvs _).expects(Set(existingConv.id)).once().returning(Future.successful(IndexedSeq(
        ConversationMemberData(self,      existingConv.id),
        ConversationMemberData(otherUserId, existingConv.id)
      )))

      (convsStorage.getAll _).expects(Seq(existingConv.id)).once().returning(Future.successful(Seq(Some(existingConv))))

      result(initService.getOrCreateOneToOneConversation(otherUserId)) shouldEqual existingConv
    }

    scenario("Existing 1:1 conversation between two team members with NAME should not be returned") {
      val otherUserId = UserId("otherUser")
      val otherUser = UserData(otherUserId, team, "other", searchKey = SearchKey.simple("other"))

      val name = Some("Conv Name")
      val existingConv = ConversationData(creator = self, name = name, convType = Group, team = team)

      (userStorage.get _).expects(otherUserId).once().returning(Future.successful(Some(otherUser)))

      (members.getByUsers _).expects(Set(otherUserId)).once().returning(Future.successful(IndexedSeq(
        ConversationMemberData(otherUserId, existingConv.id)
      )))

      (members.getByConvs _).expects(Set(existingConv.id)).once().returning(Future.successful(IndexedSeq(
        ConversationMemberData(self,      existingConv.id),
        ConversationMemberData(otherUserId, existingConv.id)
      )))

      (convsStorage.getAll _).expects(Seq(existingConv.id)).once().returning(Future.successful(Seq(Some(existingConv))))
      (convsContent.createConversationWithMembers _).expects(*, *, Group, self, Set(otherUserId), None, false, Set(Access.INVITE, Access.CODE), AccessRole.NON_ACTIVATED).once().onCall {
        (conv: ConvId, r: RConvId, tpe: ConversationType, cr: UserId, us: Set[UserId], n: Option[String], hid: Boolean, ac: Set[Access], ar: AccessRole) =>
          Future.successful(ConversationData(conv, r, n, cr, tpe, team, hidden = hid, access = ac, accessRole = Some(ar)))
      }
      (messages.addConversationStartMessage _).expects(*, self, Set(otherUserId), None, None).once().returning(Future.successful(null))
      (sync.postConversation _).expects(*, Set(otherUserId), None, team, Set(Access.INVITE, Access.CODE), AccessRole.NON_ACTIVATED).once().returning(Future.successful(null))

      val conv = result(initService.getOrCreateOneToOneConversation(otherUserId))
      conv shouldNot equal(existingConv)
    }
  }

  feature("Conversations with guests") {

    //TODO under what circumstances is the user connection status "Ignored"? What happens if you're just unconnected with that person?
    scenario("Create 1:1 conversation with a non-team member should create a real 1:1 conversation") {
      val otherUserId = UserId("otherUser")
      val otherUser = UserData(otherUserId, Some(TeamId("different_team")), "other", searchKey = SearchKey.simple("other"), connection = ConnectionStatus.Ignored)

      val expectedConv = ConversationData(ConvId("otherUser"), creator = self, convType = OneToOne, team = None)

      (userStorage.get _).expects(otherUserId).twice().returning(Future.successful(Some(otherUser)))

      (convsContent.convById _).expects(ConvId("otherUser")).returning(Future.successful(None))
      (convsContent.createConversationWithMembers _).expects(ConvId("otherUser"), *, Incoming, otherUserId, Set(self), None, true, Set(Access.PRIVATE), AccessRole.PRIVATE).once().onCall {
        (conv: ConvId, r: RConvId, tpe: ConversationType, cr: UserId, us: Set[UserId], n: Option[String], hid: Boolean, ac: Set[Access], ar: AccessRole) =>
          Future.successful(ConversationData(conv, r, n, cr, tpe, team, hidden = hid, access = ac, accessRole = Some(ar)))
      }

      (messages.addMemberJoinMessage _).expects(ConvId("otherUser"), otherUserId, Set(self), true, false).once().returning(Future.successful(null))

      val conv = result(initService.getOrCreateOneToOneConversation(otherUserId))
      conv.id shouldEqual ConvId("otherUser")
    }
  }

  def initService: ConversationsUiService =
    new ConversationsUiServiceImpl(self, team, null, userStorage, messages, null, null, members, null, convsContent, convsStorage, null, null, sync, null, null, null, null)
}
