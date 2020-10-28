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
package com.waz.zclient.messages.controllers

import android.app.Activity
import android.content.DialogInterface.OnDismissListener
import android.content._
import androidx.core.app.ShareCompat
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService
import com.waz.service.assets.Asset.Image
import com.waz.service.messages.MessageAndLikes
import com.wire.signals.CancellableFuture
import com.waz.utils._
import com.wire.signals.{EventContext, EventStream, Signal}
import com.waz.zclient.common.controllers.AssetsController.AssetForShare
import com.waz.zclient.common.controllers.ScreenController.MessageDetailsParams
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.common.controllers.{AssetsController, ScreenController}
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.{LikesAndReadsFragment, ReplyController}
import com.waz.zclient.messages.MessageBottomSheetDialog
import com.waz.zclient.messages.MessageBottomSheetDialog.{MessageAction, Params}
import com.waz.zclient.participants.OptionsMenu
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ExternalFileSharing
import com.waz.zclient.{ClipboardUtils, Injectable, Injector, R}
import com.waz.zclient.log.LogUI._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class MessageActionsController(implicit injector: Injector, ctx: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {

  import MessageActionsController._
  import com.waz.threading.Threading.Implicits.Ui

  private val context                   = inject[Activity]
  private lazy val keyboardController   = inject[KeyboardController]
  private lazy val userPrefsController  = inject[IUserPreferencesController]
  private lazy val clipboard            = inject[ClipboardUtils]
  private lazy val permissions          = inject[PermissionsService]
  private lazy val replyController      = inject[ReplyController]
  private lazy val screenController = inject[ScreenController]
  private lazy val externalFileSharing = inject[ExternalFileSharing]

  private lazy val assetsController     = inject[AssetsController]

  private val zms = inject[Signal[ZMessaging]]

  val onMessageAction = EventStream[(MessageAction, MessageData)]()

  val onDeleteConfirmed = EventStream[(MessageData, Boolean)]() // Boolean == isRecall(true) or localDelete(false)
  val onAssetSaved = EventStream[AssetData]()

  val messageToReveal = Signal[Option[MessageData]](None)

  private var dialog = Option.empty[OptionsMenu]

  onMessageAction {
    case (MessageAction.Copy, message)             => copyMessage(message)
    case (MessageAction.DeleteGlobal, message)     => recallMessage(message)
    case (MessageAction.DeleteLocal, message)      => deleteMessage(message)
    case (MessageAction.Forward, message)          => forwardMessage(message)
    case (MessageAction.Save, message)             => saveMessage(message)
    case (MessageAction.Reveal, message)           => revealMessageInConversation(message)
    case (MessageAction.Delete, message)           => promptDeleteMessage(message)
    case (MessageAction.Like, msg) =>
      zms.head.flatMap(_.reactions.like(msg.convId, msg.id)) foreach { _ =>
        userPrefsController.setPerformedAction(IUserPreferencesController.LIKED_MESSAGE)
      }
    case (MessageAction.Unlike, msg) =>
      zms.head.flatMap(_.reactions.unlike(msg.convId, msg.id))
    case (MessageAction.Reply, message)             => replyMessage(message)
    case (MessageAction.Details, message)           => showDetails(message)
    case _ => // should be handled somewhere else
  }

  private val onDismissed = new OnDismissListener {
    override def onDismiss(dialogInterface: DialogInterface): Unit = dialog = None
  }

  def showDialog(data: MessageAndLikes, fromCollection: Boolean = false): Boolean = {
    // TODO: keyboard should be handled in more generic way
    (if (keyboardController.hideKeyboardIfVisible()) CancellableFuture.delayed(HideDelay){}.future else Future.successful({})).map { _ =>
      dialog.foreach(_.dismiss())
      dialog = Some(
        returning(OptionsMenu(context, new MessageBottomSheetDialog(data.message, Params(collection = fromCollection)))) { d =>
          d.setOnDismissListener(onDismissed)
          d.show()
        }
      )
    }.recoverWithLog()
    true
  }

  def showDeleteDialog(message: MessageData): Unit = {
    OptionsMenu(context, new MessageBottomSheetDialog(message,
                                 Params(collection = true, delCollapsed = false),
                                 Seq(MessageAction.DeleteLocal, MessageAction.DeleteGlobal))).show()

  }

  private def copyMessage(message: MessageData) =
    zms.head.flatMap(_.usersStorage.get(message.userId)) foreach {
      case Some(user) =>
        val clip = ClipData.newPlainText(getString(R.string.conversation__action_mode__copy__description, user.name), message.contentString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.conversation__action_mode__copy__toast, Toast.LENGTH_SHORT).show()
      case None =>
        // invalid message, ignoring
    }

  private def deleteMessage(message: MessageData) =
    showDeleteDialog(R.string.conversation__message_action__delete_for_me) {
      zms.head.flatMap(_.convsUi.deleteMessage(message.convId, message.id)) foreach { _ =>
        onDeleteConfirmed ! (message, false)
      }
    }

  private def recallMessage(message: MessageData) =
    showDeleteDialog(R.string.conversation__message_action__delete_for_everyone) {
      zms.head.flatMap(_.convsUi.recallMessage(message.convId, message.id)) foreach { _ =>
        onDeleteConfirmed ! (message, true)
      }
    }

  private def promptDeleteMessage(message: MessageData) =
    zms.head.map(_.selfUserId) foreach {
      case user if user == message.userId => showDeleteDialog(message)
      case _ => deleteMessage(message)
    }

  private def replyMessage(data: MessageData): Unit = replyController.replyToMessage(data.id, data.convId)

  private def showDetails(data: MessageData): Unit = screenController.showMessageDetails ! Some(MessageDetailsParams(data.id, LikesAndReadsFragment.LikesTab))

  private def showDeleteDialog(title: Int)(onSuccess: => Unit) =
    new AlertDialog.Builder(context)
      .setTitle(title)
      .setMessage(R.string.conversation__message_action__delete_details)
      .setCancelable(true)
      .setNegativeButton(R.string.conversation__message_action__delete__dialog__cancel, null)
      .setPositiveButton(R.string.conversation__message_action__delete__dialog__ok, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          onSuccess
          Toast.makeText(context, R.string.conversation__message_action__delete__confirmation, Toast.LENGTH_SHORT).show()
        }
      })
      .create()
      .show()

  private def forwardMessage(message: MessageData): Unit = message.assetId match {
    case Some(id: AssetId) => forwardAssetMessage(id)
    case None => forwardOtherMessage(message)
    case _ => // TODO: show error info?
  }

  private def forwardAssetMessage(id: AssetId): Unit = {
    val intentBuilder = ShareCompat.IntentBuilder.from(context)
    intentBuilder.setChooserTitle(R.string.conversation__action_mode__fwd__chooser__title)
    val dialog = new AlertDialog.Builder(context)
      .setTitle(R.string.conversation__action_mode__fwd__dialog__title)
      .setMessage(R.string.conversation__action_mode__fwd__dialog__message)
      .setCancelable(true)
      .create()

    dialog.show()

    assetsController.assetForSharing(id).onComplete {

      case Success(AssetForShare(asset, file)) =>
        dialog.dismiss()
        val mime =
          if (asset.mime.str.equals("text/plain"))
            "text/*"
          else if (asset.mime == Mime.Unknown)
            Mime.Default.str
          else
            asset.mime.str
        intentBuilder.setType(mime)
        intentBuilder.addStream(externalFileSharing.getUriForFile(file))
        intentBuilder.startChooser()

      case Failure(err) =>
        error(l"Asset $id is not for sharing", err)
        dialog.dismiss()
    }
  }

  private def forwardOtherMessage(message: MessageData): Unit = {
    val intentBuilder = ShareCompat.IntentBuilder.from(context)
    intentBuilder.setChooserTitle(R.string.conversation__action_mode__fwd__chooser__title)
    intentBuilder.setType("text/plain")
    intentBuilder.setText(message.contentString)
    intentBuilder.startChooser()
  }

  private def saveMessage(message: MessageData): Unit =
    message.assetId.collect { case id: AssetId => id }.foreach { assetId =>
      for {
        assets <- inject[Signal[AssetService]].head
        asset  <- assets.getAsset(assetId)
      } {
        asset.details match {
          case _: Image => assetsController.saveImageToGallery(asset)
          case _        => assetsController.saveToDownloads(asset)
        }
      }
  }

  private def revealMessageInConversation(message: MessageData) = {
    zms.head.flatMap(z => z.messagesStorage.get(message.id)).onComplete {
      case Success(msg) =>  messageToReveal ! msg
      case _ =>
    }
  }
}

object MessageActionsController {
  private val HideDelay = 200.millis
}
