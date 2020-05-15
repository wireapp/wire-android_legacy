/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

package com.waz.zclient.messages

import android.content.Context
import com.waz.api.Message
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetStatus
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceStream}
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.messages.MessageBottomSheetDialog.{Actions, MessageAction, Params}
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.participants.OptionsMenuController
import com.waz.zclient.participants.OptionsMenuController.MenuItem
import com.waz.zclient.{Injectable, Injector, R, WireApplication}

class MessageBottomSheetDialog(message: MessageData,
                               params: Params,
                               operations: Seq[MessageAction] = Seq.empty)(implicit injector: Injector, context: Context, ec: EventContext)
  extends OptionsMenuController with Injectable {

  lazy val zmessaging = inject[Signal[ZMessaging]]
  lazy val messageActionsController = inject[MessageActionsController]
  lazy val assetsController = inject[AssetsController]

  override val title: Signal[Option[String]] = Signal.const(None)
  override val optionItems: Signal[Seq[OptionsMenuController.MenuItem]] =
    zmessaging.flatMap { zms =>
    val all = if (operations.isEmpty) Actions else operations
    Signal.sequence(all.map { action =>
      action.enabled(message, zms, params, assetsController) map {
        case true => Some(action)
        case false => Option.empty[MessageAction]
      }
    } :_*).map(_.flatten)
  }

  override val onMenuItemClicked: SourceStream[OptionsMenuController.MenuItem] = EventStream()
  override val selectedItems: Signal[Set[OptionsMenuController.MenuItem]] = Signal.const(Set())

  onMenuItemClicked {
    case action: MessageAction =>
      messageActionsController.onMessageAction ! (action, message)
    case _ =>
  }
}


object MessageBottomSheetDialog {
  val CollectionExtra = "COLLECTION_EXTRA"
  val DelCollapsedExtra = "DEL_COLLAPSED_EXTRA"

  // all possible actions
  val Actions = {
    import MessageAction._
    Seq(Copy, OpenFile, Edit, Like, Unlike, Reply, Details, Save, Forward, Delete, DeleteLocal, DeleteGlobal, Reveal)
  }

  case class Params(collection: Boolean = false, delCollapsed: Boolean = true)

  def isMemberOfConversation(conv: ConvId, zms: ZMessaging) =
    zms.membersStorage.optSignal((zms.selfUserId, conv)) map (_.isDefined)

  def isAssetDataReady(assetId: GeneralAssetId, assets: AssetsController): Signal[Boolean] =
    assets.assetStatusSignal(assetId) map {
      case (AssetStatus.Done, _) => true
      case _ => false
    }

  def isLikedBySelf(msg: MessageId, zms: ZMessaging) =
    zms.reactionsStorage.optSignal((msg, zms.selfUserId)) map {
      case Some(liking) => liking.action == Liking.Action.Like
      case None => false
    }

  //TODO: Remove glyphId
  abstract class MessageAction(val resId: Int, val glyphResId: Int, val stringId: Int) extends MenuItem {

    override val title: String = WireApplication.APP_INSTANCE.getString(stringId)
    override val iconId: Option[Int] = Some(resId)
    override val colorId: Option[Int] = None

    def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean]
  }

  object MessageAction {
    import Message.Type._

    case object Forward extends MessageAction(R.id.message_bottom_menu_item_forward, R.string.glyph__share, R.string.message_bottom_menu_action_forward) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] = {
        if (msg.isEphemeral) Signal.const(false)
        else msg.msgType match {
          case TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | IMAGE_ASSET           =>
            Signal.const(true)
          case ANY_ASSET | AUDIO_ASSET | VIDEO_ASSET =>
            isAssetDataReady(msg.assetId.get, assets)
          case _ =>
            Signal.const(false)
        }
      }
    }

    case object Copy extends MessageAction(R.id.message_bottom_menu_item_copy, R.string.glyph__copy, R.string.message_bottom_menu_action_copy) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] =
        msg.msgType match {
          case TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA if zms.selfUserId == msg.userId || !msg.isEphemeral => Signal.const(true)
          case _ => Signal.const(false)
        }
    }

    case object Delete extends MessageAction(R.id.message_bottom_menu_item_delete, R.string.glyph__trash, R.string.message_bottom_menu_action_delete) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] =
        msg.msgType match {
          case TEXT | ANY_ASSET | IMAGE_ASSET | AUDIO_ASSET | VIDEO_ASSET | KNOCK | LOCATION | RICH_MEDIA | TEXT_EMOJI_ONLY if p.delCollapsed =>
            if (msg.userId != zms.selfUserId) Signal.const(true)
            else isMemberOfConversation(msg.convId, zms)
          case _ =>
            Signal.const(false)
        }
    }

    case object DeleteLocal extends MessageAction(R.id.message_bottom_menu_item_delete_local, R.string.glyph__delete_me, R.string.message_bottom_menu_action_delete_local) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] = Signal const !p.delCollapsed
    }

    case object DeleteGlobal extends MessageAction(R.id.message_bottom_menu_item_delete_global, R.string.glyph__delete_everywhere, R.string.message_bottom_menu_action_delete_global) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] = {
        msg.msgType match {
          case TEXT | ANY_ASSET | IMAGE_ASSET | AUDIO_ASSET | VIDEO_ASSET | KNOCK | LOCATION | RICH_MEDIA | TEXT_EMOJI_ONLY if !p.delCollapsed =>
            if (msg.userId != zms.selfUserId) Signal const true
            else isMemberOfConversation(msg.convId, zms)
          case _ =>
            Signal const false
        }
      }
    }

    case object Like extends MessageAction(R.id.message_bottom_menu_item_like, R.string.glyph__like, R.string.message_bottom_menu_action_like) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] =
        msg.msgType match {
          case ANY_ASSET | IMAGE_ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET if !msg.isEphemeral =>
            for {
              isMember <- isMemberOfConversation(msg.convId, zms)
              isLiked <- isLikedBySelf(msg.id, zms)
            } yield isMember && !isLiked
          case _ =>
            Signal const false
        }
    }

    case object Unlike extends MessageAction(R.id.message_bottom_menu_item_unlike, R.string.glyph__liked, R.string.message_bottom_menu_action_unlike) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] =
        msg.msgType match {
          case ANY_ASSET | IMAGE_ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET if !msg.isEphemeral =>
            for {
              isMember <- isMemberOfConversation(msg.convId, zms)
              isLiked <- isLikedBySelf(msg.id, zms)
            } yield isMember && isLiked
          case _ =>
            Signal const false
        }
    }

    case object Save extends MessageAction(R.id.message_bottom_menu_item_save, R.string.glyph__download, R.string.message_bottom_menu_action_save) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] =
        msg.msgType match {
          case IMAGE_ASSET => Signal.const(zms.selfUserId == msg.userId || !msg.isEphemeral)
          case AUDIO_ASSET | VIDEO_ASSET if zms.selfUserId == msg.userId || !msg.isEphemeral => isAssetDataReady(msg.assetId.get, assets)
          case _ => Signal const false
        }
    }

    case object OpenFile extends MessageAction(R.id.message_bottom_menu_item_open_file, R.string.glyph__file, R.string.message_bottom_menu_action_open) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] = {
        msg.msgType match {
          case ANY_ASSET if !msg.isEphemeral && !p.collection =>
            isAssetDataReady(msg.assetId.get, assets)
          case _ =>
            Signal const false
        }
      }
    }

    case object Reveal extends MessageAction(R.id.message_bottom_menu_item_reveal, R.string.glyph__view, R.string.message_bottom_menu_action_reveal) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] = Signal const (p.collection && !msg.isEphemeral)
    }

    case object Edit extends MessageAction(R.id.message_bottom_menu_item_edit, R.string.glyph__edit, R.string.message_bottom_menu_action_edit) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] =
        msg.msgType match {
          case TEXT_EMOJI_ONLY | TEXT | RICH_MEDIA if !msg.isEphemeral && msg.userId == zms.selfUserId && !msg.isFailed =>
            if (p.collection) Signal const false
            else isMemberOfConversation(msg.convId, zms)
          case _ =>
            Signal const false
        }
    }

    case object Reply extends MessageAction(R.id.message_bottom_menu_item_reply, R.string.glyph__undo, R.string.message_bottom_menu_action_reply) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] = msg.msgType match {
        case ANY_ASSET | IMAGE_ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET if !msg.isEphemeral =>
          isMemberOfConversation(msg.convId, zms)
        case _ =>
          Signal.const(false)
      }
    }

    case object Details extends MessageAction(R.id.message_bottom_menu_item_details, R.string.glyph__view, R.string.message_bottom_menu_action_details) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params, assets: AssetsController): Signal[Boolean] = msg.msgType match {
        case ANY_ASSET | IMAGE_ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET | COMPOSITE if !msg.isEphemeral =>
          for {
            isGroup <- zms.conversations.groupConversation(msg.convId)
            isMember <- isMemberOfConversation(msg.convId, zms)
          } yield isGroup && isMember && !p.collection
        case _ =>
          Signal.const(false)
      }
    }

  }
}
