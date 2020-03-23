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
package com.waz.sync.handler
import com.waz.api.{Message, NetworkMode}
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.cache.CacheService
import com.waz.content.{MembersStorage, MessagesStorage}
import com.waz.model._
import com.waz.service.assets.{AssetService, AssetStorage, UploadAssetStorage}
import com.waz.service.{ErrorsService, Timeouts}
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.otr.OtrClientsService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncHandler.RequestInfo
import com.waz.sync.SyncResult.Failure
import com.waz.sync.{SyncResult, SyncServiceHandle}
import com.waz.sync.otr.OtrSyncHandler

import scala.concurrent.Future

class MessagesSyncHandlerSpec extends AndroidFreeSpec {

  val service       = mock[MessagesService]
  val msgContent    = mock[MessagesContentUpdater]
  val clients       = mock[OtrClientsService]
  val otrSync       = mock[OtrSyncHandler]
  val convs         = mock[ConversationsContentUpdater]
  val storage       = mock[MessagesStorage]
  val sync          = mock[SyncServiceHandle]
  val assets        = mock[AssetService]
  val errors        = mock[ErrorsService]
  val assetStorage  = mock[AssetStorage]
  val uploads       = mock[UploadAssetStorage]
  val cache         = mock[CacheService]
  val members       = mock[MembersStorage]
  val timeouts      = new Timeouts()

  def getHandler: MessagesSyncHandler = {
    new MessagesSyncHandler(account1Id, service, msgContent, clients, otrSync, convs, storage, sync, assets, assetStorage, uploads, cache, members, tracking, errors, timeouts)
  }

  scenario("post invalid message should fail immediately") {

    val convId = ConvId()
    val messageId = MessageId()
    implicit val requestInfo: RequestInfo = RequestInfo(0, clock.instant(), Option(NetworkMode.WIFI))

    (storage.getMessage _).expects(messageId).returning(Future.successful(None))

    result(getHandler.postMessage(convId, messageId, RemoteInstant.Epoch)).isInstanceOf[Failure] should be(true)
  }

  scenario("post message with no internet should fail immediately") {

    val convId = ConvId()
    val messageId = MessageId()
    val message = MessageData(messageId, convId = convId)
    val connectionError = ErrorResponse(ErrorResponse.ConnectionErrorCode, "", "")
    implicit val requestInfo: RequestInfo = RequestInfo(0, clock.instant(), Option(NetworkMode.OFFLINE))

    (storage.getMessage _).expects(messageId).returning(Future.successful(Option(message)))
    (convs.convById _).expects(convId).returning(Future.successful(Option(ConversationData(convId))))

    (otrSync.postOtrMessage _).expects(convId, *, * ,*, *).returning(Future.successful(Left(connectionError)))

    (service.messageDeliveryFailed _).expects(convId, message, connectionError).returning(Future.successful(Some(message.copy(state = Message.Status.FAILED))))

    result(getHandler.postMessage(convId, messageId, RemoteInstant.Epoch)).isInstanceOf[Failure] should be(true)
  }

  scenario("post button action") {

    val convId = ConvId()
    val messageId = MessageId()
    val buttonId = ButtonId()
    val senderId = UserId()

    (storage.get _).expects(messageId).anyNumberOfTimes().returning(Future.successful(Option(MessageData(messageId, convId = convId))))
    (otrSync.postOtrMessage _).expects(convId, *, * ,*, *).returning(Future.successful(Right(RemoteInstant.Epoch)))

    result(getHandler.postButtonAction(messageId, buttonId, senderId)) shouldEqual SyncResult.Success
  }

  scenario("post button action fails if the message is missing") {

    val messageId = MessageId()
    val buttonId = ButtonId()
    val senderId = UserId()

    (storage.get _).expects(messageId).anyNumberOfTimes().returning(Future.successful(None))

    result(getHandler.postButtonAction(messageId, buttonId, senderId)) shouldEqual SyncResult.Failure("message not found")
  }

  scenario("when post button action fails, sets button error on db") {

    val convId = ConvId()
    val messageId = MessageId()
    val buttonId = ButtonId()
    val senderId = UserId()
    val errorText = "Error"

    (storage.get _).expects(messageId).anyNumberOfTimes().returning(Future.successful(Option(MessageData(messageId, convId = convId))))
    (otrSync.postOtrMessage _).expects(convId, *, * ,*, *).returning(Future.successful(Left(internalError(errorText))))
    (service.setButtonError _).expects(messageId, buttonId).once().returning(Future.successful({}))

    result(getHandler.postButtonAction(messageId, buttonId, senderId)) shouldEqual SyncResult.Failure(errorText)
  }

}
