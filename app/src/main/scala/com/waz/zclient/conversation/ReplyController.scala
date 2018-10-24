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
import com.waz.zclient.messages.UsersController.DisplayName
import com.waz.zclient.messages.{MessagesController, UsersController}
import com.waz.zclient.{Injectable, Injector}

class ReplyController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {

  private val conversationController = inject[ConversationController]
  private val messagesController = inject[MessagesController]
  private val usersController = inject[UsersController]
  private val assetsController = inject[AssetsController]

  val replyData: SourceSignal[Option[(MessageId, ConvId)]] = Signal(None)

  val replyContent: Signal[Option[ReplyContent]] = (for {
    Some((msgId, _)) <- replyData
    Some(msg) <- messagesController.getMessage(msgId)
    sender <- usersController.displayName(msg.userId)
    asset <- assetsController.assetSignal(msg.assetId).map(a => Option(a._1)).orElse(Signal.const(Option.empty[AssetData]))
  } yield Option(ReplyContent(msg, asset, sender))).orElse(Signal.const(None))

  conversationController.currentConvId.zip(replyData) {
    case (currentConv, Some((_, replyConv))) if currentConv != replyConv =>
      replyData ! None
    case _ =>
  }

  def replyToMessage(msg: MessageId, convId: ConvId): Unit = replyData ! Some((msg, convId))
  def clearMessage(): Unit = replyData ! None
}

case class ReplyContent(message: MessageData, asset: Option[AssetData], sender: DisplayName)
