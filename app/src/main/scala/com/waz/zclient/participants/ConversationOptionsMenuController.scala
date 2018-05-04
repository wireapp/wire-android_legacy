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
package com.waz.zclient.participants

import android.content.{Context, DialogInterface}
import android.support.v7.app.AlertDialog
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.model.{ConvId, ConversationData, UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events._
import com.waz.zclient.calling.controllers.CallStartController
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.participants.ConversationOptionsMenuController._
import com.waz.zclient.participants.OptionsMenuController._
import com.waz.zclient.utils.ContextUtils.{getInt, getString}
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.duration._

class ConversationOptionsMenuController(convId: ConvId, mode: Mode)(implicit injector: Injector, context: Context, ec: EventContext) extends OptionsMenuController with Injectable {
  import Threading.Implicits.Ui

  private val zMessaging             = inject[Signal[ZMessaging]]
  private val convController         = inject[ConversationController]
  private val participantsController = inject[ParticipantsController]
  private val navController          = inject[INavigationController]
  private val users                  = inject[UsersController]
  private val callingController      = inject[CallStartController]
  private val cameraController       = inject[ICameraController]
  private val screenController       = inject[IConversationScreenController]

  override val onMenuItemClicked: SourceStream[MenuItem] = EventStream()

  lazy val tag: String = if (mode.inConversationList) "OptionsMenu_ConvList" else "OptionsMenu_Participants"

  val conv: Signal[Option[ConversationData]] = convController.conversationData(convId)

  //returns Signal(None) if the selected convId is a group
  val otherUser: Signal[Option[UserData]] = (for {
    zms          <- zMessaging
    isGroup      <- Signal.future(zms.conversations.isGroupConversation(convId))
    id <- if (isGroup) Signal.const(Option.empty[UserId]) else zms.membersStorage.activeMembers(convId).map(_.filter(_ != zms.selfUserId)).map(_.headOption)
    user <- id.fold(Signal.const(Option.empty[UserData]))(zms.users.userSignal(_).map(Some(_)))
  } yield user)
    .orElse(Signal.const(Option.empty[UserData]))

  val isGroup: Signal[Boolean] = otherUser.map(_.isEmpty)

  val isMember: Signal[Boolean] = for {
    zms <- zMessaging
    conv <- conv
    members <- conv.fold(Signal.const(Set.empty[UserId]))(cd => zms.membersStorage.activeMembers(cd.id))
  } yield members.contains(zms.selfUserId)

  val optionItems: Signal[Seq[MenuItem]] = for {
    zms           <- zMessaging
    Some(conv)    <- conv
    isGroup       <- isGroup
    connectStatus <- otherUser.map(_.map(_.connection))
    teamMember    <- otherUser.map(_.exists(u => u.teamId.nonEmpty && u.teamId == zms.teamId))
    isBot         <- otherUser.map(_.exists(_.isWireBot))
  } yield {
    import com.waz.api.User.ConnectionStatus._

    val builder = Set.newBuilder[MenuItem]

    mode match {
      case Mode.Leaving(_) =>
        builder ++= Set(LeaveOnly, LeaveAndDelete)
      case Mode.Deleting(_) =>
        builder ++= Set(DeleteOnly, DeleteAndLeave)
      case Mode.Normal(_) =>
        builder += (if (conv.archived) Unarchive else Archive)
        if (isGroup) {
          if (conv.isActive) {
            builder += (if (conv.muted) Unmute else Mute)
            builder += Leave
          }
          builder += Delete
        } else {
          if (teamMember || connectStatus.contains(ACCEPTED) || isBot) {
            builder += (if (conv.muted) Unmute else Mute)
            builder += Delete
            if (!teamMember && connectStatus.contains(ACCEPTED)) builder ++= Set(Block)
          }
          else if (connectStatus.contains(PENDING_FROM_USER)) builder += Block
        }
    }
    builder.result().toSeq.sortWith {
      case (a, b) => OrderSeq.indexOf(a).compareTo(OrderSeq.indexOf(b)) < 0
    }
  }

  private val convState = otherUser.map(other => (convId, other))

  (new EventStreamWithAuxSignal(onMenuItemClicked, convState)) {
    case (item, Some((cId, user))) =>
      verbose(s"onMenuItemClicked: item: $item, conv: $cId, user: $user")
      item match {
        case Archive   =>
          convController.archive(cId, archive = true)
          if (!mode.inConversationList) CancellableFuture.delay(getInt(R.integer.framework_animation_duration_medium).millis).map { _ =>
            navController.setVisiblePage(Page.CONVERSATION_LIST, tag)
            participantsController.onHideParticipants ! true
          }

        case Unarchive => convController.archive(cId, archive = false)
        case Mute   => convController.setMuted(cId, muted = true)
        case Unmute => convController.setMuted(cId, muted = false)
        case Leave     => leaveConversation(cId)
        case Delete    => deleteConversation(cId)
        case Block     => user.map(_.id).foreach(showBlockConfirmation(cId, _))
        case Unblock   => user.map(_.id).foreach(uId => zMessaging.head.flatMap(_.connection.unblockConnection(uId)))
        case Call      => callConversation(cId)
        case Picture   => takePictureInConversation(cId)
        case _ =>
      }
    case _ =>
  }

  private def leaveConversation(convId: ConvId): Unit = {
    val dialog = new AlertDialog.Builder(context, R.style.Theme_Light_Dialog_Alert_Destructive)
      .setCancelable(true)
      .setTitle(R.string.confirmation_menu__meta_remove)
      .setMessage(R.string.confirmation_menu__meta_remove_text)
      .setPositiveButton(R.string.conversation__action__leave_only, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = convController.leave(convId)
      }).setNegativeButton(R.string.conversation__action__leave_and_delete, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = convController.delete(convId, alsoLeave = true)
      }).create
    dialog.show()
  }

  def deleteConversation(convId: ConvId): Unit = {
    isGroup.head.flatMap { isGroup =>
      isMember.head.map { isMember =>
        val dialogBuilder = new AlertDialog.Builder(context, R.style.Theme_Light_Dialog_Alert_Destructive)
          .setCancelable(true)
          .setTitle(R.string.confirmation_menu__meta_delete)
          .setMessage(R.string.confirmation_menu__meta_delete_text)
          .setPositiveButton(R.string.conversation__action__delete_only, new DialogInterface.OnClickListener {
            override def onClick(dialog: DialogInterface, which: Int): Unit = convController.delete(convId, alsoLeave = false)
          })
        if (isGroup && isMember) {
          dialogBuilder.setNegativeButton(R.string.conversation__action__delete_and_leave, new DialogInterface.OnClickListener {
            override def onClick(dialog: DialogInterface, which: Int): Unit = convController.delete(convId, alsoLeave = true)
          })
        }
        dialogBuilder.create.show()
      }
    }
  }

  private def showBlockConfirmation(convId: ConvId, userId: UserId) =
    (for {
      curConvId <- convController.currentConvId.map(Option(_)).orElse(Signal.const(Option.empty[ConvId])).head
      displayName <- users.displayNameString(userId).head
    } yield (curConvId, displayName)).map {
      case (curConvId, displayName) =>
        val dialog = new AlertDialog.Builder(context, R.style.Theme_Light_Dialog_Alert_Destructive)
          .setCancelable(true)
          .setTitle(R.string.confirmation_menu__block_header)
          .setMessage(getString(R.string.confirmation_menu__block_text_with_name, displayName))
          .setNegativeButton(R.string.confirmation_menu__confirm_block, new DialogInterface.OnClickListener {
            override def onClick(dialog: DialogInterface, which: Int): Unit = {
              zMessaging.head.flatMap(_.connection.blockConnection(userId)).map { _ =>
                if (!mode.inConversationList || curConvId.contains(convId))
                  convController.setCurrentConversationToNext(ConversationChangeRequester.BLOCK_USER)

                if (!mode.inConversationList) {
                  screenController.hideUser()
                }
              }(Threading.Ui)
            }
          }).create
        dialog.show()
    }(Threading.Ui)

  private def callConversation(convId: ConvId) = {
    verbose(s"callConversation $convId")
    convController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST).map { _ =>
      callingController.startCallInCurrentConv(withVideo = false)
    }
  }

  private def takePictureInConversation(convId: ConvId) = {
    verbose(s"sendPictureToConversation $convId")
    convController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST).map { _ =>
      cameraController.openCamera(CameraContext.MESSAGE)
    }
  }

  override def finalize(): Unit = {
    verbose("finalized!")
  }
}

object ConversationOptionsMenuController {

  sealed trait Mode {
    val inConversationList: Boolean
  }
  object Mode{
    case class Normal(inConversationList: Boolean) extends Mode
    case class Deleting(inConversationList: Boolean) extends Mode
    case class Leaving(inConversationList: Boolean) extends Mode
  }

  object Picture   extends MenuItem(R.string.conversation__action__picture, Some(R.string.glyph__camera))
  object Call      extends MenuItem(R.string.conversation__action__call, Some(R.string.glyph__call))

  object Mute      extends MenuItem(R.string.conversation__action__silence, Some(R.string.glyph__silence))
  object Unmute    extends MenuItem(R.string.conversation__action__unsilence, Some(R.string.glyph__notify))
  object Archive   extends MenuItem(R.string.conversation__action__archive, Some(R.string.glyph__archive))
  object Unarchive extends MenuItem(R.string.conversation__action__unarchive, Some(R.string.glyph__archive))
  object Delete    extends MenuItem(R.string.conversation__action__delete, Some(R.string.glyph__delete_me))
  object Leave     extends MenuItem(R.string.conversation__action__leave, Some(R.string.glyph__leave))
  object Block     extends MenuItem(R.string.conversation__action__block, Some(R.string.glyph__block))
  object Unblock   extends MenuItem(R.string.conversation__action__unblock, Some(R.string.glyph__block))

  object LeaveOnly      extends MenuItem(R.string.conversation__action__leave_only, Some(R.string.empty_string))
  object LeaveAndDelete extends MenuItem(R.string.conversation__action__leave_and_delete, Some(R.string.empty_string))
  object DeleteOnly     extends MenuItem(R.string.conversation__action__delete_only, Some(R.string.empty_string))
  object DeleteAndLeave extends MenuItem(R.string.conversation__action__delete_and_leave, Some(R.string.empty_string))

  val OrderSeq = Seq(Mute, Unmute, Archive, Unarchive, Delete, Leave, Block, Unblock, LeaveOnly, LeaveAndDelete, DeleteOnly, DeleteAndLeave)

}
