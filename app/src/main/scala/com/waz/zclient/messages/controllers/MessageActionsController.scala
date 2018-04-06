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

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.{Activity, ProgressDialog}
import android.content.DialogInterface.OnDismissListener
import android.content._
import android.support.v4.app.ShareCompat
import android.support.v7.app.AlertDialog
import android.widget.Toast
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils._
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.messages.MessageBottomSheetDialog
import com.waz.zclient.messages.MessageBottomSheetDialog.{MessageAction, Params}
import com.waz.zclient.notifications.controllers.ImageNotificationsController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

class MessageActionsController(implicit injector: Injector, ctx: Context, ec: EventContext) extends Injectable {
  import MessageActionsController._
  import com.waz.threading.Threading.Implicits.Ui

  private val context                   = inject[Activity]
  private lazy val keyboardController   = inject[KeyboardController]
  private lazy val userPrefsController  = inject[IUserPreferencesController]
  private lazy val clipboardManager     = inject[ClipboardManager]
  private lazy val permissions          = inject[PermissionsService]
  private lazy val imageNotifications   = inject[ImageNotificationsController]

  private val zms = inject[Signal[ZMessaging]]

  val onMessageAction = EventStream[(MessageAction, MessageData)]()

  val onDeleteConfirmed = EventStream[(MessageData, Boolean)]() // Boolean == isRecall(true) or localDelete(false)
  val onAssetSaved = EventStream[AssetData]()

  val messageToReveal = Signal[Option[MessageData]]()

  private var dialog = Option.empty[MessageBottomSheetDialog]

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
    case _ => // should be handled somewhere else
  }

  private val onDismissed = new OnDismissListener {
    override def onDismiss(dialogInterface: DialogInterface): Unit = dialog = None
  }

  def showDialog(data: MessageAndLikes, fromCollection: Boolean = false): Boolean = {
    // TODO: keyboard should be handled in more generic way
    if (keyboardController.hideKeyboardIfVisible()) CancellableFuture.delayed(HideDelay){}.future else Future.successful({}).map { _ =>
      dialog.foreach(_.dismiss())
      dialog = Some(
        returning(new MessageBottomSheetDialog(context, R.style.message__bottom_sheet__base, data.message, Params(collection = fromCollection))) { d =>
          d.setOnDismissListener(onDismissed)
          d.show()
        }
      )
    }.recoverWithLog()
    true
  }

  def showDeleteDialog(message: MessageData): Unit = {
    new MessageBottomSheetDialog(context,
                                 R.style.message__bottom_sheet__base, message,
                                 Params(collection = true, delCollapsed = false),
                                 Seq(MessageAction.DeleteLocal, MessageAction.DeleteGlobal))
      .show()
  }

  private def copyMessage(message: MessageData) =
    zms.head.flatMap(_.users.getUser(message.userId)) foreach {
      case Some(user) =>
        val clip = ClipData.newPlainText(getString(R.string.conversation__action_mode__copy__description, user.getDisplayName), message.contentString)
        clipboardManager.setPrimaryClip(clip)
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

  private def getAsset(assetId: AssetId) = for {
    z <- zms.head
    asset <- z.assets.getAssetData(assetId)
    uri <- z.assets.getContentUri(assetId)
  } yield (asset, uri)


  private def forwardMessage(message: MessageData) = {
    val intentBuilder = ShareCompat.IntentBuilder.from(context)
    intentBuilder.setChooserTitle(R.string.conversation__action_mode__fwd__chooser__title)
    if (message.isAssetMessage) {
      val dialog = ProgressDialog.show(context,
        getString(R.string.conversation__action_mode__fwd__dialog__title),
        getString(R.string.conversation__action_mode__fwd__dialog__message), true, true, null)

      getAsset(message.assetId) foreach {
        case (Some(data), Some(uri)) =>
          dialog.dismiss()
          val mime =
            if (data.mime.str.equals("text/plain"))
              "text/*"
            else if (data.mime == Mime.Unknown)
              //TODO: should be fixed on file receiver side
              Mime.Default.str
            else
              data.mime.str
          intentBuilder.setType(mime)
          intentBuilder.addStream(AndroidURIUtil.unwrap(uri))
          intentBuilder.startChooser()
        case _ =>
          // TODO: show error info
          dialog.dismiss()
      }
    } else { // TODO: handle location and other non text messages
      intentBuilder.setType("text/plain")
      intentBuilder.setText(message.contentString)
      intentBuilder.startChooser()
    }
  }

  private def saveMessage(message: MessageData) =
    permissions.requestAllPermissions(Set(WRITE_EXTERNAL_STORAGE)).map {  // TODO: provide explanation dialog - use requiring with message str
      case true =>
        if (message.msgType == Message.Type.ASSET) { // TODO: simplify once SE asset v3 is merged, we should be able to handle that without special conditions

          val saveFuture = for {
            z <- zms.head
            asset <- z.assets.getAssetData(message.assetId) if asset.isDefined
            uri <- z.imageLoader.saveImageToGallery(asset.get)
          } yield uri

          saveFuture onComplete {
            case Success(Some(uri)) =>
              imageNotifications.showImageSavedNotification(message.assetId, uri)
              Toast.makeText(context, R.string.message_bottom_menu_action_save_ok, Toast.LENGTH_SHORT).show()
            case _ =>
              Toast.makeText(context, com.waz.zclient.ui.R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
          }
        } else {
          val dialog = ProgressDialog.show(context, getString(R.string.conversation__action_mode__fwd__dialog__title), getString(R.string.conversation__action_mode__fwd__dialog__message), true, true, null)
          zms.head.flatMap(_.assets.saveAssetToDownloads(message.assetId)) foreach {
            case Some(file) =>
              zms.head.flatMap(_.assets.getAssetData(message.assetId)) foreach {
                case Some(data) => onAssetSaved ! data
                case None => // should never happen
              }
              Toast.makeText(context, com.waz.zclient.ui.R.string.content__file__action__save_completed, Toast.LENGTH_SHORT).show()
              context.sendBroadcast(returning(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE))(_.setData(AndroidURIUtil.unwrap(URI.fromFile(file)))))
              dialog.dismiss()
            case None =>
              Toast.makeText(context, com.waz.zclient.ui.R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
              dialog.dismiss()
          }
        }
      case false =>
    } (Threading.Ui)

  private def revealMessageInConversation(message: MessageData) = {
    zms.head.flatMap(z => z.messagesStorage.get(message.id)).onComplete{
      case Success(msg) =>  messageToReveal ! msg
      case _ =>
    }
  }
}

object MessageActionsController {
  private val HideDelay = 200.millis
}
