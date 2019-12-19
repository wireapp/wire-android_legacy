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
import androidx.appcompat.app.AlertDialog
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events._
import com.waz.zclient.calling.controllers.CallStartController
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.messages.UsersController.DisplayName.Other
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.participants.ConversationOptionsMenuController._
import com.waz.zclient.participants.OptionsMenuController._
import com.waz.zclient.utils.ContextUtils.{getInt, getString}
import com.waz.zclient.{Injectable, Injector, R, WireApplication}

import scala.concurrent.Future
import scala.concurrent.duration._

class ConversationOptionsMenuController(convId: ConvId, mode: Mode, fromDeepLink: Boolean = false)
                                       (implicit injector: Injector, context: Context, ec: EventContext)
  extends OptionsMenuController
    with Injectable
    with DerivedLogTag {

  import Threading.Implicits.Ui

  private val zMessaging             = inject[Signal[ZMessaging]]
  private val convController         = inject[ConversationController]
  private val participantsController = inject[ParticipantsController]
  private val navController          = inject[INavigationController]
  private val users                  = inject[UsersController]
  private val callingController      = inject[CallStartController]
  private val cameraController       = inject[ICameraController]
  private val screenController       = inject[IConversationScreenController]
  private val convListController     = inject[ConversationListController]

  override val onMenuItemClicked: SourceStream[MenuItem] = EventStream()
  override val selectedItems: Signal[Set[MenuItem]] = Signal.const(Set())
  override val title: Signal[Option[String]] =
    if (mode.inConversationList)
      Signal.future(convController.getConversation(convId).map(_.map(_.displayName)))
    else
      Signal.const(None)

  lazy val tag: String = if (mode.inConversationList) "OptionsMenu_ConvList" else "OptionsMenu_Participants"

  val conv: Signal[Option[ConversationData]] = convController.conversationData(convId)

  //returns Signal(None) if the selected convId is a group
  val otherUser: Signal[Option[UserData]] = (for {
    zms          <- zMessaging
    isGroup      <- zms.conversations.groupConversation(convId)
    id <- if (isGroup) Signal.const(Option.empty[UserId]) else zms.membersStorage.activeMembers(convId).map(_.filter(_ != zms.selfUserId)).map(_.headOption)
    user <- id.fold(Signal.const(Option.empty[UserData]))(zms.usersStorage.signal(_).map(Some(_)))
  } yield user)
    .orElse(Signal.const(Option.empty[UserData]))

  val isGroup: Signal[Boolean] = otherUser.map(_.isEmpty)

  val isMember: Signal[Boolean] = for {
    zms <- zMessaging
    conv <- conv
    members <- conv.fold(Signal.const(Set.empty[UserId]))(cd => zms.membersStorage.activeMembers(cd.id))
  } yield members.contains(zms.selfUserId)

  val teamId: Signal[Option[TeamId]] = zMessaging.map(_.teamId)

  val optionItems: Signal[Seq[MenuItem]] = for {
    teamId               <- teamId
    Some(conv)           <- conv
    isGroup              <- isGroup
    connectStatus        <- otherUser.map(_.map(_.connection))
    teamMember           <- otherUser.map(_.exists(u => u.teamId.nonEmpty && u.teamId == teamId))
    isBot                <- otherUser.map(_.exists(_.isWireBot))
    selfRole             <- convController.selfRoleInConv(convId)
    isCurrentUserCreator <- Signal.future(convController.isCurrentUserCreator(convId))
    selectedParticipant  <- participantsController.selectedParticipant
    favoriteConvIds      <- convListController.favoriteConversations.map(convs => convs.map(_.id))
    customFolderId       <- Signal.future(convListController.getCustomFolderId(convId))
    customFolderData     <- customFolderId.fold(Signal.const[Option[FolderData]](None))(convListController.folder)
  } yield {
    import com.waz.api.User.ConnectionStatus._

    val builder = Set.newBuilder[MenuItem]

    mode match {
      case Mode.Leaving(_) =>
        if (selfRole.canLeaveConversation) builder ++= Set(LeaveOnly, LeaveAndClear)

      case Mode.Deleting(_) =>
        builder += ClearOnly
        if (selfRole.canLeaveConversation) builder += ClearAndLeave

      case Mode.Normal(false) if fromDeepLink =>
        if (connectStatus.contains(ACCEPTED) || connectStatus.contains(PENDING_FROM_USER)) builder += Block
        else if (connectStatus.contains(BLOCKED)) builder += Unblock

      case Mode.Normal(false) if isGroup && selectedParticipant.isDefined =>
        if (selfRole.canRemoveGroupMember) builder += RemoveMember

      case Mode.Normal(inConversationList) =>

        def notifications: MenuItem =
          if (teamId.isDefined)
            Notifications
          else if (conv.muted.isAllAllowed)
            Mute
          else
            Unmute

        val isConvFavorite = favoriteConvIds.contains(convId)
        
        (conv.archived, isConvFavorite) match {
          case (true, _)           => builder += Unarchive
          case (false, isFavorite) => builder ++= List(Archive, if (isFavorite) RemoveFromFavorites else AddToFavorites)
        }

        if (isGroup) {
          if (conv.isActive) builder += Leave
          if (mode.inConversationList || teamId.isEmpty) builder += notifications
          builder += Clear
          if (!inConversationList && isCurrentUserCreator && selfRole.canDeleteGroup) builder += DeleteGroupConv
        } else {
          if (teamMember || connectStatus.contains(ACCEPTED) || isBot) {
            builder ++= Set(notifications, Clear)
            if (!teamMember && connectStatus.contains(ACCEPTED)) builder += Block
          }
          else if (connectStatus.contains(PENDING_FROM_USER)) builder += Block
        }

        builder += MoveToFolder
        customFolderData.foreach(data => builder += RemoveFromFolder(data))
    }
    builder.result().toSeq.sortWith {
      case (_: RemoveFromFolder, b) => OrderSeq.indexOf(RemoveFromFolderPlaceHolder).compareTo(OrderSeq.indexOf(b)) < 0
      case (a, _: RemoveFromFolder) => OrderSeq.indexOf(a).compareTo(OrderSeq.indexOf(RemoveFromFolderPlaceHolder)) < 0
      case (a, b) => OrderSeq.indexOf(a).compareTo(OrderSeq.indexOf(b)) < 0
    }
  }

  private val convState = otherUser.map(other => (convId, other))

  private def switchToConversationList() =
    if (!mode.inConversationList) CancellableFuture.delay(getInt(R.integer.framework_animation_duration_medium).millis).map { _ =>
      navController.setVisiblePage(Page.CONVERSATION_LIST, tag)
      participantsController.onLeaveParticipants ! true
    }

  new EventStreamWithAuxSignal(onMenuItemClicked, convState).apply {
    case (item, Some((cId, user))) =>
      verbose(l"onMenuItemClicked: item: $item, conv: $cId, user: $user")
      item match {
        case Archive   =>
          convController.archive(cId, archive = true)
          switchToConversationList()
        case Mute   => convController.setMuted(cId, muted = MuteSet.AllMuted)
        case Unmute   => convController.setMuted(cId, muted = MuteSet.AllAllowed)
        case Unarchive => convController.archive(cId, archive = false)
        case Notifications => OptionsMenu(context, new NotificationsOptionsMenuController(convId, mode.inConversationList)).show()
        case Leave     => leaveConversation(cId)
        case Clear     => clearConversation(cId)
        case Block     => user.map(_.id).foreach(showBlockConfirmation(cId, _))
        case Unblock   => user.map(_.id).foreach(uId => zMessaging.head.flatMap(_.connection.unblockConnection(uId)))
        case RemoveMember =>
          participantsController.otherParticipantId.head.foreach {
            case Some(userId) => participantsController.showRemoveConfirmation(userId)
            case None =>
          }
        case Call      => callConversation(cId)
        case Picture   => takePictureInConversation(cId)
        case AddToFavorites      => convListController.addToFavorites(cId)
        case RemoveFromFavorites => convListController.removeFromFavorites(cId)
        case MoveToFolder        => screenController.showMoveToFolder(cId)
        case i: RemoveFromFolder => convListController.removeFromFolder(cId, i.folderData.id)
        case DeleteGroupConv     => deleteConversation(cId)
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
        override def onClick(dialog: DialogInterface, which: Int): Unit = {
          convController.leave(convId)
          switchToConversationList()
        }
      }).setNegativeButton(R.string.conversation__action__leave_and_clear, new DialogInterface.OnClickListener {
      override def onClick(dialog: DialogInterface, which: Int): Unit = {
        convController.delete(convId, alsoLeave = true)
        switchToConversationList()
      }
    }).create
    dialog.show()
  }

  def clearConversation(convId: ConvId): Unit = {
    isGroup.head.flatMap { isGroup =>
      isMember.head.map { isMember =>
        val dialogBuilder = new AlertDialog.Builder(context, R.style.Theme_Light_Dialog_Alert_Destructive)
          .setCancelable(true)
          .setTitle(R.string.confirmation_menu__clear_popup_title)
          .setMessage(R.string.confirmation_menu__meta_delete_text)
          .setPositiveButton(R.string.conversation__action__clear_only, new DialogInterface.OnClickListener {
            override def onClick(dialog: DialogInterface, which: Int): Unit = convController.delete(convId, alsoLeave = false)
          })
        if (isGroup && isMember) {
          dialogBuilder.setNegativeButton(R.string.conversation__action__clear_and_leave, new DialogInterface.OnClickListener {
            override def onClick(dialog: DialogInterface, which: Int): Unit = {
              convController.delete(convId, alsoLeave = true)
              switchToConversationList()
            }
          })
        }
        dialogBuilder.create.show()
      }
    }
  }

  private def showBlockConfirmation(convId: ConvId, userId: UserId) =
    (for {
      curConvId <- convController.currentConvId.map(Option(_)).orElse(Signal.const(Option.empty[ConvId])).head
      displayName <- users.displayName(userId).collect { case Other(name) => name }.head //should be impossible to get Me in this case
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
    verbose(l"callConversation $convId")
    convController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST).map { _ =>
      callingController.startCallInCurrentConv(withVideo = false)
    }
  }

  private def takePictureInConversation(convId: ConvId) = {
    verbose(l"sendPictureToConversation $convId")
    convController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST).map { _ =>
      cameraController.openCamera(CameraContext.MESSAGE)
    }
  }

  private def deleteConversation(convId: ConvId): Unit = {
    new AlertDialog.Builder(context, R.style.Theme_Light_Dialog_Alert_Destructive)
      .setCancelable(true)
      .setTitle(R.string.confirmation_menu__delete_popup_title)
      .setMessage(R.string.confirmation_menu__delete_popup_text)
      .setPositiveButton(R.string.confirmation_menu__delete_popup_positive_button, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = {
          teamId.head.flatMap {
            case Some(tId) => convListController.deleteConversation(tId, convId)
            case _         => Future.successful(())
          }
        }
      })
      .setNegativeButton(R.string.confirmation_menu__cancel, null)
      .create.show()
  }

  override def finalize(): Unit = {
    verbose(l"finalized!")
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

  case class RemoveFromFolder(folderData: FolderData) extends BaseMenuItem(
    WireApplication.APP_INSTANCE.getString(R.string.conversation__action__remove_from_folder, folderData.name.str),
    Some(R.string.glyph__remove_from_folder)
  )

  // Dummy object to hold place in OrderSeq
  object RemoveFromFolderPlaceHolder extends RemoveFromFolder(FolderData(name = ""))

  object Mute                extends BaseMenuItem(R.string.conversation__action__silence, Some(R.string.glyph__silence))
  object Unmute              extends BaseMenuItem(R.string.conversation__action__unsilence, Some(R.string.glyph__notify))
  object Picture             extends BaseMenuItem(R.string.conversation__action__picture, Some(R.string.glyph__camera))
  object Call                extends BaseMenuItem(R.string.conversation__action__call, Some(R.string.glyph__call))
  object Notifications       extends BaseMenuItem(R.string.conversation__action__notifications, Some(R.string.glyph__notify))
  object Archive             extends BaseMenuItem(R.string.conversation__action__archive, Some(R.string.glyph__archive))
  object Unarchive           extends BaseMenuItem(R.string.conversation__action__unarchive, Some(R.string.glyph__archive))
  object AddToFavorites      extends BaseMenuItem(R.string.conversation__action__add_to_favorites, Some(R.string.glyph__add_to_favorites))
  object RemoveFromFavorites extends BaseMenuItem(R.string.conversation__action__remove_from_favorites, Some(R.string.glyph__remove_from_favorites))
  object MoveToFolder        extends BaseMenuItem(R.string.conversation__action__move_to_folder, Some(R.string.glyph__move_to_folder))

  object Clear               extends BaseMenuItem(R.string.conversation__action__clear_content, Some(R.string.glyph__clear))
  object Leave               extends BaseMenuItem(R.string.conversation__action__leave, Some(R.string.glyph__leave))
  object DeleteGroupConv     extends BaseMenuItem(R.string.conversation__action__delete_group, Some(R.string.glyph__delete_me), Some(R.color.accent_red))
  object Block               extends BaseMenuItem(R.string.conversation__action__block, Some(R.string.glyph__block))
  object Unblock             extends BaseMenuItem(R.string.conversation__action__unblock, Some(R.string.glyph__block))
  object RemoveMember        extends BaseMenuItem(R.string.conversation__action__remove_member, Some(R.string.glyph__minus))

  object LeaveOnly     extends BaseMenuItem(R.string.conversation__action__leave_only, Some(R.string.empty_string))
  object LeaveAndClear extends BaseMenuItem(R.string.conversation__action__leave_and_clear, Some(R.string.empty_string))
  object ClearOnly     extends BaseMenuItem(R.string.conversation__action__clear_only, Some(R.string.empty_string))
  object ClearAndLeave extends BaseMenuItem(R.string.conversation__action__clear_and_leave, Some(R.string.empty_string))

  val OrderSeq = Seq(Mute, Unmute, Notifications, Archive, Unarchive, AddToFavorites, RemoveFromFavorites, MoveToFolder,
    RemoveFromFolderPlaceHolder, Clear, Leave, DeleteGroupConv, Block, Unblock, RemoveMember, LeaveOnly, LeaveAndClear, ClearOnly, ClearAndLeave)
}
