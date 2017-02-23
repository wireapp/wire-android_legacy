/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.tracking

import android.content.Context
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{AssetData, ConvId, MessageContentIndex, MessageId}
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient.controllers.{AssetsController, BrowserController}
import com.waz.zclient.conversation.CollectionController
import com.waz.zclient.messages.LikesController
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.main.conversation.views.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.{Injectable, Injector}
import org.threeten.bp.Duration

import scala.concurrent.Future
import scala.concurrent.Future.{apply => _}

class UiTrackingController(implicit injector: Injector, ctx: Context, ec: EventContext) extends Injectable {
  import GlobalTrackingController._
  import Threading.Implicits.Background
  import UiTrackingController._

  val global = inject[GlobalTrackingController]

  import global._

  val msgActionController   = inject[MessageActionsController]
  val assetsController      = inject[AssetsController]
  val browserController     = inject[BrowserController]
  val likesController       = inject[LikesController]
  val collectionsController = inject[CollectionController]

  msgActionController.onMessageAction {
    case (MessageAction.REVEAL, message) if MessageContentIndex.TextMessageTypes.contains(message.getMessageType) =>
      collectionsController.currentConv.currentValue.foreach { conv =>
        convTrackingData(conv).map{ data =>
          tagEvent(SelectedSearchResultCollectionsEvent(data.convType, data.withOtto))
        }
      }
    case (action, message) if collectionsController.openedCollection.currentValue.exists(_.nonEmpty) =>
      collectionsController.openedCollection.currentValue.foreach(_.foreach{ info=>
        convTrackingData(info.conversation.id).map { data =>
          tagEvent(DidItemActionCollectionsEvent(action, message.getMessageType, data.convType, data.withOtto))
        }
      })
    case (action, message) => tagEvent(OpenedMessageActionEvent(action, message.getMessageType.name))

      import MessageAction._
      action match {
        case COPY            => tagEvent(CopiedMessageEvent(message.getMessageType.name))
        case FORWARD         => tagEvent(ForwardedMessageEvent(message.getMessageType.name))
        case a@(LIKE|UNLIKE) => reactedToMessageEvent(MessageId(message.getId), a == LIKE, "menu")
        case _ => //other types handled after dialog and confirmation
      }
  }

  likesController.onLikeButtonClicked { mAndL => reactedToMessageEvent(mAndL.message.id, !mAndL.likedBySelf, "button")}

  likesController.onViewDoubleClicked { mAndL => reactedToMessageEvent(mAndL.message.id, !mAndL.likedBySelf, "douple_tap")} //sic

  private def reactedToMessageEvent(msgId: MessageId, liked: Boolean, method: String) = messageTrackingData(msgId).map {
    case MessageTrackingData(convType, msgType, withOtto, fromSelf, isLastMsg) =>
      tagEvent(ReactedToMessageEvent(liked, withOtto, fromSelf, msgType, convType, isLastMsg, method))
  }

  msgActionController.onDeleteConfirmed { case (m, forEveryone) => tagEvent(DeletedMessageEvent(m.getMessageType.name, forEveryone)) }
  msgActionController.onAssetSaved(asset => tagEvent(SavedFileEvent(asset.getMimeType, asset.getSizeInBytes.toInt)))

  assetsController.onFileOpened {
    case a@AssetData.IsVideo() => tagEvent(OpenedFileEvent(a.mime.str, a.sizeInBytes.toInt))
    case a => tagEvent(OpenedFileEvent(a.mime.str, a.sizeInBytes.toInt))
  }
  assetsController.onFileSaved(a => tagEvent(SavedFileEvent(a.mime.str, a.sizeInBytes.toInt)))

  assetsController.onVideoPlayed(a => messageTrackingData(MessageId(a.id.str)).map {
    case MessageTrackingData(convType, _, isOtto, fromSelf, _) => tagEvent(PlayedVideoMessageEvent(durationInSeconds(a), !fromSelf, isOtto, convType))
  })

  assetsController.onAudioPlayed(a => messageTrackingData(MessageId(a.id.str)).map {
    case MessageTrackingData(convType, _, isOtto, fromSelf, _) => tagEvent(PlayedAudioMessageEvent(a.mime.orDefault.str, durationInSeconds(a), !fromSelf, isOtto, convType))
  })

  browserController.onYoutubeLinkOpened(mId => messageTrackingData(mId).map {
    case MessageTrackingData(convType, _, isOtto, fromSelf, _) => tagEvent(PlayedYouTubeMessageEvent(!fromSelf, isOtto, convType))
  })

  collectionsController.openedCollection.on(Threading.Ui){
    _.foreach { info => convTrackingData(info.conversation.id).map { data =>
      tagEvent(OpenedCollectionsEvent(info.empty, collectionsController.contentSearchQuery.currentValue.exists(_.originalString.nonEmpty), data.convType, data.withOtto))}
    }
  }

  collectionsController.openContextMenuForMessage{
    m => messageTrackingData(m.id).map { data =>
      tagEvent(OpenedItemMenuCollectionsEvent(m.msgType, data.convType, data.withOtto))
    }
  }

  collectionsController.clickedMessage{
    m => messageTrackingData(m.id).map { data =>
      tagEvent(OpenedItemCollectionsEvent(m.msgType, data.convType, data.withOtto))
    }
  }

  collectionsController.contentSearchQuery.on(Threading.Ui){
    query =>
      if (query.originalString.nonEmpty) {
        collectionsController.currentConv.currentValue.foreach { conv =>
          convTrackingData(conv).map{ data =>
            tagEvent(EnteredSearchCollectionsEvent(data.convType, data.withOtto))
          }
        }
      }
  }

  private def durationInSeconds(a: AssetData): Int = (a match {
    case AssetData.WithDuration(d) => d
    case _ => Duration.ZERO
  }).toMillis.toInt / 1000

  private def messageTrackingData(messageId: MessageId): Future[MessageTrackingData] = {
    for {
      zms        <- zMessaging.head
      Some(msg)  <- zms.messagesStorage.get(messageId)
      Some(conv) <- zms.convsContent.convById(msg.convId)
      withOtto   <- isOtto(conv, zms.usersStorage)
      lastMsgs   <- zms.messagesStorage.lastMessageFromSelfAndFromOther(conv.id).head
      fromSelf    = msg.userId == zms.selfUserId
      convType    = conv.convType
      isLastMsg   = lastMsgs._1.exists(_.id == msg.id) || lastMsgs._2.exists(_.id == msg.id)
    } yield MessageTrackingData(convType, msg.msgType, withOtto, fromSelf, isLastMsg)
  }

  private def convTrackingData(convId: ConvId): Future[ConversationTrackingData] = {
    for {
      zms        <- zMessaging.head
      Some(conv) <- zms.convsContent.convById(convId)
      withOtto   <- isOtto(conv, zms.usersStorage)
    } yield ConversationTrackingData(conv.convType, withOtto)
  }
}

object UiTrackingController {

  case class MessageTrackingData(convType: ConversationType, msgType: Message.Type, withOtto: Boolean, msgFromSelf: Boolean, isLastMsg: Boolean)

  case class ConversationTrackingData(convType: ConversationType, withOtto: Boolean)

}
