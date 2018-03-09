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

import android.content.Context
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.model.ConversationData.ConversationType.isOneToOne
import com.waz.model.{ConvId, ConversationData, UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventContext, EventStream, EventStreamWithAuxSignal, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{SoundController, ThemeController, UserAccountsController}
import com.waz.zclient.controllers.calling.ICallingController
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.confirmation.{ConfirmationRequest, IConfirmationController, TwoButtonConfirmationCallback}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.participants.OptionsMenu.{AnimState, Closed, Opening}
import com.waz.zclient.ui.optionsmenu.OptionsMenuItem
import com.waz.zclient.ui.optionsmenu.OptionsMenuItem._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.Future
import scala.concurrent.duration._

class OptionsMenuController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {
  import Threading.Implicits.Ui

  val zMessaging             = inject[Signal[ZMessaging]]
  val convController         = inject[ConversationController]
  val participantsController = inject[ParticipantsController]
  val navController          = inject[INavigationController]
  val confirmationController = inject[IConfirmationController]
  val themes                 = inject[ThemeController]
  val sounds                 = inject[SoundController]
  val userAccounts           = inject[UserAccountsController]
  val users                  = inject[UsersController]
  val callingController      = inject[ICallingController]
  val cameraController       = inject[ICameraController]
  val screenController       = inject[IConversationScreenController]
  val pickUserController     = inject[IPickUserController]

  val convId = Signal(Option.empty[ConvId])

  val onMenuItemClicked = EventStream[OptionsMenuItem]()
  val animationState    = Signal[AnimState](Closed)

  val inConversationList = Signal(false)
  private def inConvList = inConversationList.currentValue.getOrElse(false)
  inConversationList(v => verbose(s"inConversationList: $v"))
  def tag = if (inConvList) "OptionsMenu_ConvList" else "OptionsMenu_Participants"

  val optionsTheme = inConversationList.flatMap {
    case true => Signal.const(themes.optionsDarkTheme)
    case _    => themes.darkThemePref.flatMap(_.signal).map {
      case true => themes.optionsDarkTheme
      case _    => themes.optionsLightTheme
    }
  }

  private def currentTheme = optionsTheme.currentValue.getOrElse(themes.optionsDarkTheme)

  val conv = convId.flatMap {
    case Some(id)   => convController.conversationData(id)
    case _          => Signal.const(Option.empty[ConversationData])
  }

  //returns Signal(None) if the selected convId is a group
  val otherUser: Signal[Option[UserData]] = (for {
    zms          <- zMessaging
    Some(convId) <- convId
    id <- zms.teamId.isDefined match {
      case true => zms.membersStorage.activeMembers(convId).filter(us => us.size == 2).map(_.find(_ != zms.selfUserId))
      case _    => zms.convsStorage.signal(convId).collect {
        case c if isOneToOne(c.convType) => Some(UserId(c.id.str))
      }
    }
    user <- id.fold(Signal.const(Option.empty[UserData]))(zms.users.userSignal(_).map(Some(_)))
  } yield user)
    .orElse(Signal.const(Option.empty[UserData]))
  otherUser (v => verbose(s"otherUser: $v"))

  val isGroup = otherUser.map(_.isEmpty)

  val isMember = for {
    zms <- zMessaging
    conv <- conv
    members <- conv.fold(Signal.const(Set.empty[UserId]))(cd => zms.membersStorage.activeMembers(cd.id))
  } yield members.contains(zms.selfUserId)

  val optionItems = for {
    zms           <- zMessaging
    Some(conv)    <- conv
    isGroup       <- isGroup
    connectStatus <- otherUser.map(_.map(_.connection))
    teamMember    <- otherUser.map(_.exists(u => u.teamId.nonEmpty && u.teamId == zms.teamId))
    isBot         <- otherUser.map(_.exists(_.isWireBot))
    inConvList    <- inConversationList
  } yield {
    import OptionsMenuItem._
    import com.waz.api.User.ConnectionStatus._

    val builder = Set.newBuilder[OptionsMenuItem]

    builder += (if (conv.archived) UNARCHIVE else ARCHIVE)
    isGroup match {
      case false =>
        if (teamMember || connectStatus.contains(ACCEPTED) || isBot) {
          builder += (if (conv.muted) UNSILENCE else SILENCE)
          builder += DELETE
          if (inConvList) builder ++= Set(CALL, PICTURE)
          if (!teamMember && connectStatus.contains(ACCEPTED)) builder ++= Set(BLOCK)
        }
        else if (connectStatus.contains(PENDING_FROM_USER)) builder += BLOCK
      case true =>
        if (conv.isActive) {
          builder +=  (if (conv.muted) UNSILENCE else SILENCE)
          builder ++= (if (inConvList) Set(CALL, PICTURE) else Set(RENAME))
          builder +=  LEAVE
        }
        builder += (if (conv.archived) UNARCHIVE else ARCHIVE)
        builder += DELETE
    }
    builder.result().toSeq.sortWith { case (l, r) => l.compareTo(r) < 0 }
  }

  (for {
    inConvList <- inConversationList
    animState  <- animationState
  } yield (inConvList, animState)).onChanged.collect {
    case (true, Opening) => Page.CONVERSATION_MENU_OVER_CONVERSATION_LIST
    case (true, Closed)  => Page.CONVERSATION_LIST
  }.onUi(page => navController.setLeftPage(page, tag))

  val convState = for {
    cId <- convId
    other <- otherUser
  } yield (cId, other)

  (new EventStreamWithAuxSignal(onMenuItemClicked, convState)) {
    case (item, Some((Some(cId), user))) =>
      verbose(s"onMenuItemClicked: item: $item, conv: $cId, user: $user")
      item match {
        case ARCHIVE   =>
          convController.archive(cId, archive = true)
          if (!inConvList) CancellableFuture.delay(getInt(R.integer.framework_animation_duration_medium).millis).map { _ =>
            navController.setVisiblePage(Page.CONVERSATION_LIST, tag)
            participantsController.onHideParticipants ! {}
          }

        case UNARCHIVE => convController.archive(cId, archive = false)
        case SILENCE   => convController.setMuted(cId, muted = true)
        case UNSILENCE => convController.setMuted(cId, muted = false)
        case LEAVE     => leaveConversation(cId)
        case DELETE    => deleteConversation(cId)
        case BLOCK     => user.map(_.id).foreach(showBlockConfirmation(cId, _))
        case UNBLOCK   => zMessaging.head.flatMap { zms =>
          user.map(_.id) match {
            case Some(uId) => zms.connection.unblockConnection(uId)
            case _         => Future.successful({})
          }
        }
        case CALL      => callConversation(cId)
        case PICTURE   => takePictureInConversation(cId)
        case RENAME    => screenController.editConversationName(true)
        case _ =>
      }
    case _ =>
  }

  private def leaveConversation(convId: ConvId) =
    requestConfirmation(
      header  = getString(R.string.confirmation_menu__meta_remove),
      text    = getString(R.string.confirmation_menu__meta_remove_text),
      confirm = getString(R.string.confirmation_menu__confirm_leave),
      cancel  = getString(R.string.confirmation_menu__cancel),
      onPositiveClick = _ => {
        convController.leave(convId)
      }
    )

  def deleteConversation(convId: ConvId) = {
    import Threading.Implicits.Ui
    isGroup.head.flatMap { isGroup =>
      isMember.head.map { isMember =>
        requestConfirmation(
          header  = getString(R.string.confirmation_menu__meta_delete),
          text    = getString(R.string.confirmation_menu__meta_delete_text),
          confirm = getString(R.string.confirmation_menu__confirm_delete),
          cancel  = getString(R.string.confirmation_menu__cancel),
          checkBoxLabel = if (isGroup && isMember) getString(R.string.confirmation_menu__delete_conversation__checkbox__label) else "",
          onHideAnimEnd = (confirmed, _, checkboxIsSelected) => {
            if (confirmed) convController.delete(convId, checkboxIsSelected)
          }
        )
      }
    }
  }

  private def showBlockConfirmation(convId: ConvId, userId: UserId) =
    (for {
      curConvId <- convController.currentConvId.head
      displayName <- users.displayNameString(userId).head
    } yield (curConvId, displayName)).map {
      case (curConvId, displayName) =>
        requestConfirmation(
          header  = getString(R.string.confirmation_menu__block_header),
          text    = getString(R.string.confirmation_menu__block_text_with_name, displayName),
          confirm = getString(R.string.confirmation_menu__confirm_block),
          cancel  = getString(R.string.confirmation_menu__cancel),
          onPositiveClick = _ => {
            zMessaging.head.flatMap(_.connection.blockConnection(userId)).map { _ =>
              if (!inConvList || convId == curConvId)
                  convController.setCurrentConversationToNext(ConversationChangeRequester.BLOCK_USER)

              if (!inConvList) {
                screenController.hideUser()
              }
            }(Threading.Ui)
          }
        )
    }(Threading.Ui)

  private def requestConfirmation(header:  String,
                                  text:    String,
                                  confirm: String,
                                  cancel:  String,
                                  checkBoxLabel: String = "",
                                  onPositiveClick: Boolean => Unit = _ => {},
                                  onHideAnimEnd:   (Boolean, Boolean, Boolean) => Unit = (_, _, _) => {},
                                  checkBoxSelected: Boolean = false) = {
    val callback = new TwoButtonConfirmationCallback() {
      override def positiveButtonClicked(checkboxIsSelected: Boolean) = onPositiveClick(checkboxIsSelected)
      override def negativeButtonClicked() = {}
      override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean) = onHideAnimEnd(confirmed, canceled, checkboxIsSelected)
    }
    val request = returning(new ConfirmationRequest.Builder()) { r =>
      r.withHeader(header)
        .withMessage(text)
        .withPositiveButton(confirm)
        .withNegativeButton(cancel)
        .withConfirmationCallback(callback)
        .withCheckboxLabel(checkBoxLabel)
        .withWireTheme(currentTheme)

      if (checkBoxSelected) r.withCheckboxSelectedByDefault()

    }.build()

    confirmationController.requestConfirmation(request, if (inConvList) IConfirmationController.CONVERSATION_LIST else IConfirmationController.PARTICIPANTS)
    sounds.playAlert()
  }

  private def callConversation(convId: ConvId) = {
    verbose(s"callConversation $convId")
    convController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST).map { _ =>
      callingController.startCall(false)
    }
  }

  private def takePictureInConversation(convId: ConvId) = {
    verbose(s"sendPictureToConversation $convId")
    convController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST).map { _ =>
      cameraController.openCamera(CameraContext.MESSAGE)
    }
  }
}
