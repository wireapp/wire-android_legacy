/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.conversation

import android.content.Context
import com.waz.model.{AssetData, ConvId, MessageData, MessageId}
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.messages.{MessagesController, UsersController}
import com.waz.zclient.{Injectable, Injector}
import com.waz.ZLog.ImplicitTag.implicitLogTag
import scala.concurrent.Future

class ReplyController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {

  import com.waz.threading.Threading.Implicits.Background

  private val conversationController = inject[ConversationController]
  private val messagesController = inject[MessagesController]
  private val usersController = inject[UsersController]
  private val assetsController = inject[AssetsController]

  val replyData: SourceSignal[Map[ConvId, MessageId]] = Signal(Map())

  def replyContent(convId: ConvId): Signal[Option[ReplyContent]] = (for {
    Some(msgId) <- replyData.map(_.get(convId))
    Some(msg) <- messagesController.getMessage(msgId)
    sender <- usersController.displayNameStringIncludingSelf(msg.userId)
    asset <- assetsController.assetSignal(msg.assetId).map(a => Option(a._1)).orElse(Signal.const(Option.empty[AssetData]))
  } yield Option(ReplyContent(msg, asset, sender))).orElse(Signal.const(None))

  val currentReplyContent: Signal[Option[ReplyContent]] = conversationController.currentConvId.flatMap(replyContent)

  def replyToMessage(msg: MessageId, convId: ConvId): Boolean = replyData.mutate { _ + (convId -> msg) }
  def clearMessage(convId: ConvId): Boolean = replyData.mutate { _ - convId }

  def replyInCurrentConversation(msg: MessageId): Future[Boolean] = conversationController.currentConvId.head.map(replyToMessage(msg, _))
  def clearMessageInCurrentConversation(): Future[Boolean] = conversationController.currentConvId.head.map(clearMessage)
}

case class ReplyContent(message: MessageData, asset: Option[AssetData], sender: String)
