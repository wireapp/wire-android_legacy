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
package com.waz.service.otr

import com.waz.api.Verification
import com.waz.api.impl.ErrorResponse
import com.waz.content.{ConversationStorage, MembersStorage, UsersStorage}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model.otr.ClientId
import com.waz.model._
import com.waz.service.conversation.ConversationsService
import com.waz.service.push.PushService
import com.waz.service.{ErrorsService, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.OtrClient.{ClientMismatch, EncryptedContent, MessageResponse}
import com.waz.sync.client.{AssetClient, MessagesClient, OtrClient}
import com.waz.sync.otr.OtrSyncHandler.OtrMessage
import com.waz.sync.otr.{OtrClientsSyncHandler, OtrSyncHandlerImpl}
import com.waz.threading.CancellableFuture

import scala.concurrent.Future

class OtrSyncHandlerSpec extends AndroidFreeSpec {

  val teamId             = Some(TeamId())
  val selfClientId       = ClientId("client-id")
  val otrClient          = mock[OtrClient]
  val msgClient          = mock[MessagesClient]
  val assetClient        = mock[AssetClient]
  val service            = mock[OtrService]
  val convStorage        = mock[ConversationStorage]
  val convsService       = mock[ConversationsService]
  val users              = mock[UserService]
  val members            = mock[MembersStorage]
  val errors             = mock[ErrorsService]
  val clientsSyncHandler = mock[OtrClientsSyncHandler]
  val push               = mock[PushService]
  val usersStorage       = mock[UsersStorage]

  scenario("Encrypt and send message with no errors") {

    val conv = ConversationData(ConvId("conv-id"), RConvId("r-conv-id"))
    val msg = TextMessage("content", Nil)

    val otherUser = UserId("other-user-id")
    val otherUsersClient = ClientId("client-id")
    val content = "content".getBytes

    val encryptedContent = EncryptedContent(Map(otherUser -> Map(otherUsersClient -> content)))

    (convStorage.get _)
      .expects(conv.id)
      .returning(Future.successful(Some(conv)))

    (members.getActiveUsers _)
      .expects(conv.id)
      .returning(Future.successful(Seq(account1Id, otherUser)))

    (service.encryptForUsers _)
      .expects(Set(account1Id, otherUser), msg, *, EncryptedContent.Empty)
      .returning(Future.successful(encryptedContent))

    (msgClient.postMessage _)
      .expects(conv.remoteId, OtrMessage(selfClientId, encryptedContent, nativePush = true), *, Option.empty[Set[UserId]])
      .returning(CancellableFuture.successful(Right(MessageResponse.Success(ClientMismatch(time = RemoteInstant.Epoch)))))

    (service.deleteClients _)
      .expects(Map.empty[UserId, Seq[ClientId]])
      .returning(Future.successful({}))

    (convsService.addUnexpectedMembersToConv _)
      .expects(conv.id, Set.empty[UserId])
      .returning(Future.successful({}))

    val sh = getSyncHandler
    result(sh.postOtrMessage(conv.id, msg))
  }

  scenario("Can't encrypt or send message in unverified conversation") {
    val conv = ConversationData(ConvId("conv-id"), RConvId("r-conv-id"), verified = Verification.UNVERIFIED)

    (convStorage.get _)
      .expects(conv.id)
      .returning(Future.successful(Some(conv)))

    val sh = getSyncHandler
    //Send calling message to avoid triggering errors service
    result(sh.postOtrMessage(conv.id, GenericMessage(Uid(), GenericContent.Calling("msg")))) shouldEqual Left(ErrorResponse.Unverified)

  }


  scenario("Unexpected users and/or clients in missing response should be updated and added to members, and previously encrypted content should be updated") {

    val conv = ConversationData(ConvId("conv-id"), RConvId("r-conv-id"))
    val msg = TextMessage("content", Seq.empty)

    val otherUser        = UserId("other-user-id")
    val otherUsersClient = ClientId("other-user-client-1")
    val content          = "content".getBytes

    val encryptedContent1 = EncryptedContent(Map(otherUser -> Map(otherUsersClient -> content)))

    val missingUser       = UserId("missing-user-id")
    val missingUserClient = ClientId("missing-user-client-1")
    val contentForMissing = "content".getBytes
    val missing = Map(missingUser -> Seq(missingUserClient))

    val encryptedContent2 = EncryptedContent(
      encryptedContent1.content ++
        Map(missingUser -> Map(missingUserClient -> contentForMissing))
    )

    (convStorage.get _)
      .expects(conv.id)
      .twice()
      .returning(Future.successful(Some(conv)))

    var callsToActiveMembers = 0
    (members.getActiveUsers _)
      .expects(conv.id)
      .twice()
      .onCall { _: ConvId =>
        Future.successful {
          callsToActiveMembers += 1
          callsToActiveMembers match {
            case 1 =>
              Seq(account1Id, otherUser)
            case 2 =>
              Seq(account1Id, otherUser, missingUser)
            case _ => fail("Unexpected number of calls to postMessage")
          }
        }
      }

    var callsToEncrypt = 0
    (service.encryptForUsers _)
      .expects(*, msg, *, *)
      .twice()
      .onCall { (us, _, _, previous) =>
        callsToEncrypt += 1
        callsToEncrypt match {
          case 1 =>
            us shouldEqual Set(account1Id, otherUser)
            previous shouldEqual EncryptedContent.Empty
            Future.successful(encryptedContent1)
          case 2 =>
            us shouldEqual Set(account1Id, otherUser, missingUser)
            previous shouldEqual encryptedContent1
            Future.successful(encryptedContent2)
        }
      }

    var callsToPostMessage = 0
    (msgClient.postMessage _)
      .expects(conv.remoteId, *, *, Option.empty[Set[UserId]])
      .twice()
      .onCall { (_, msg, ignoreMissing: Boolean, _) =>
        CancellableFuture.successful(Right {
          callsToPostMessage += 1
          ignoreMissing shouldEqual false
          msg.sender shouldEqual selfClientId
          callsToPostMessage match {
            case 1 =>
              msg.recipients shouldEqual encryptedContent1
              MessageResponse.Failure(ClientMismatch(missing = missing, time = RemoteInstant.Max))
            case 2 =>
              msg.recipients shouldEqual encryptedContent2
              MessageResponse.Success(ClientMismatch(time = RemoteInstant.Max))
            case _ => fail("Unexpected number of calls to postMessage")
          }
        })
      }

    (service.deleteClients _)
      .expects(Map.empty[UserId, Seq[ClientId]])
      .twice()
      .returning(Future.successful({}))

    var callsToAddUnexpectedMembers = 0
    (convsService.addUnexpectedMembersToConv _)
      .expects(conv.id, *)
      .twice()
      .onCall { (_, us) =>
        callsToAddUnexpectedMembers += 1
        callsToAddUnexpectedMembers match {
          case 1 => us.size shouldEqual 1
            Future.successful(Some(SyncId()))
          case _ => us.size shouldEqual 0
            Future.successful(Option.empty[SyncId])
        }
      }

    (clientsSyncHandler.syncSessions _).expects(missing).returning(Future.successful(None))

    val sh = getSyncHandler
    result(sh.postOtrMessage(conv.id, msg))
  }

  def getSyncHandler = {
    (push.waitProcessing _).expects().anyNumberOfTimes.returning(Future.successful({}))
    new OtrSyncHandlerImpl(teamId, selfClientId, otrClient, msgClient, assetClient, service, convsService, convStorage, users, members, errors, clientsSyncHandler, push, usersStorage)
  }

}
