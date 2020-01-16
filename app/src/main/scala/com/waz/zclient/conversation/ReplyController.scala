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
import com.waz.model._
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.messages.{MessagesController, UsersController}
import com.waz.zclient.{Injectable, Injector}
import com.waz.content.MessagesStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.assets.GeneralAsset
import com.waz.service.messages.MessagesService

import scala.concurrent.Future

class ReplyController(implicit injector: Injector, context: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  private val conversationController = inject[ConversationController]
  private val messagesController     = inject[MessagesController]
  private val usersController        = inject[UsersController]
  private val assetsController       = inject[AssetsController]
  private val messagesService        = inject[Signal[MessagesService]]
  private val messagesStorage        = inject[Signal[MessagesStorage]]

  private val replyData: SourceSignal[Map[ConvId, MessageId]] = Signal(Map())

  val currentReplyContent: Signal[Option[ReplyContent]] = (for {
    replies     <- replyData
    Some(msgId) <- conversationController.currentConvId.map(replies.get)
    msg         <- messagesController.getMessage(msgId)
    sender      <- usersController.user(msg.userId)
    asset       <- assetsController.assetSignal(msg.assetId)
  } yield Option(ReplyContent(msg, asset, sender.name))).orElse(Signal.const(None))

  messagesService.flatMap(ms => Signal.wrap(ms.msgEdited)) { case (from, to) =>
    replyData.mutate { data =>
      data.find(_._2 == from).map(_._1).fold(data) { conv =>
        data + (conv -> to)
      }
    }
  }

  messagesStorage.flatMap(ms => Signal.wrap(ms.onDeleted)) { deletedIds =>
    replyData.mutate(_.filterNot(c => deletedIds.contains(c._2)))
  }

  def replyToMessage(msg: MessageId, convId: ConvId): Boolean = replyData.mutate { _ + (convId -> msg) }
  def clearMessage(convId: ConvId): Boolean = replyData.mutate { _ - convId }

  def clearMessageInCurrentConversation(): Future[Boolean] = conversationController.currentConvId.head.map(clearMessage)
}

case class ReplyContent(message: MessageData, asset: Option[GeneralAsset], sender: String)
