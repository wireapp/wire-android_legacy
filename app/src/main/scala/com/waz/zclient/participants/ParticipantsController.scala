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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.{ConnectionService, ZMessaging}
import com.waz.threading.Threading
import com.wire.signals.{EventContext, EventStream, Signal}
import com.waz.zclient.common.controllers.{SoundController, ThemeController}
import com.waz.zclient.controllers.confirmation.{ConfirmationRequest, IConfirmationController, TwoButtonConfirmationCallback}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.ParticipantsController.{ParticipantRequest, ParticipantsFlags}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.Future

class ParticipantsController(implicit injector: Injector, context: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  private implicit lazy val uiStorage     = inject[UiStorage]
  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val convController         = inject[ConversationController]
  private lazy val confirmationController = inject[IConfirmationController]
  private lazy val screenController       = inject[IConversationScreenController]
  private lazy val usersController        = inject[UsersController]

  lazy val selectedParticipant = Signal(Option.empty[UserId])

  val onShowParticipants = EventStream[Option[String]]() //Option[String] = fragment tag //TODO use type?
  val onLeaveParticipants = EventStream[Boolean]() //Boolean represents with or without animations
  val onShowParticipantsWithUserId = EventStream[ParticipantRequest]()

  val onShowUser = EventStream[Option[UserId]]()

  lazy val otherParticipants: Signal[Map[UserId, ConversationRole]] = convController.currentConvOtherMembers
  lazy val participants: Signal[Map[UserId, ConversationRole]]      = convController.currentConvMembers
  lazy val conv: Signal[ConversationData]                           = convController.currentConv
  lazy val isGroup: Signal[Boolean]                                 = convController.currentConvIsGroup
  lazy val selfRole: Signal[ConversationRole]                       = convController.selfRole

  lazy val otherParticipantId: Signal[Option[UserId]] = Signal.zip(otherParticipants, selectedParticipant).map {
    case (others, _) if others.size == 1                               => others.headOption.map(_._1)
    case (others, selected) if selected.exists(others.keySet.contains) => selected
    case _                                                             => None
  }

  lazy val otherParticipant: Signal[UserData] = for {
    z        <- zms
    Some(id) <- otherParticipantId
    user     <- z.usersStorage.signal(id)
  } yield user

  lazy val otherParticipantExists: Signal[Boolean] = for {
    z           <- zms
    flags       <- flags
    groupOrBot  =  flags.isGroup || flags.hasBot
    userId      <- if (groupOrBot) Signal.const(Option.empty[UserId]) else otherParticipantId
    participant <- userId.fold(Signal.const(Option.empty[UserData]))(id => z.usersStorage.optSignal(id))
  } yield groupOrBot || participant.exists(!_.deleted)

  lazy val flags: Signal[ParticipantsFlags] = for {
    z            <- zms
    others       <- otherParticipants
    isGroup      <- isGroup
    users        <- Signal.sequence(others.map(p => z.usersStorage.signal(p._1)).toSeq: _*)
    hasGuest     =  isGroup && users.exists(u => u.isGuest(z.teamId, Some(z.selfDomain)) && !u.isWireBot && !usersController.isFederated(u))
    hasFederated =  isGroup && users.exists(usersController.isFederated)
    hasExternal  =  isGroup && users.exists(_.isExternal(z.teamId))
    hasBot       =  users.exists(_.isWireBot)
  } yield ParticipantsFlags(isGroup, hasGuest, hasBot, hasExternal, hasFederated)

  // is the current user a guest in the current conversation
  lazy val isCurrentUserGuest: Signal[Boolean] = for {
    z           <- zms
    currentUser <- UserSignal(z.selfUserId)
    currentConv <- conv
  } yield currentConv.team.isDefined && currentConv.team != currentUser.teamId

  lazy val currentUserBelongsToConversationTeam: Signal[Boolean] = for {
    z           <- zms
    currentUser <- UserSignal(z.selfUserId)
    currentConv <- conv
  } yield currentConv.team.isDefined && currentConv.team == currentUser.teamId

  def selectParticipant(userId: UserId): Unit = selectedParticipant ! Some(userId)

  def unselectParticipant(): Unit = selectedParticipant ! None

  def getUser(userId: UserId): Future[Option[UserData]] = zms.head.flatMap(_.usersStorage.get(userId))

  def blockUser(userId: UserId): Future[Option[UserData]] = for {
    connection <- inject[Signal[ConnectionService]].head
    user       <- connection.blockConnection(userId)
    _          =  unselectParticipant()
  } yield user

  def unblockUser(userId: UserId): Future[ConversationData] = zms.head.flatMap(_.connection.unblockConnection(userId))

  def showRemoveConfirmation(userId: UserId): Unit = getUser(userId).foreach {
    case Some(userData) =>
      val request = new ConfirmationRequest.Builder()
        .withHeader(getString(R.string.confirmation_menu__header))
        .withMessage(getString(R.string.confirmation_menu_text_with_name, userData.name))
        .withPositiveButton(getString(R.string.confirmation_menu__confirm_remove))
        .withNegativeButton(getString(R.string.confirmation_menu__cancel))
        .withConfirmationCallback(new TwoButtonConfirmationCallback() {
          override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit = {
            screenController.hideUser()
            convController.removeMember(userId)
            selectedParticipant.mutate {
              case Some(uId) if uId == userId => None
              case other                      => other
            }
          }

          override def negativeButtonClicked(): Unit = {}
          override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean): Unit = {}
        })
        .withWireTheme(inject[ThemeController].getThemeDependentOptionsTheme)
        .build
      confirmationController.requestConfirmation(request, IConfirmationController.PARTICIPANTS)
      inject[SoundController].playAlert()
    case _ =>
  }(Threading.Ui)

  def setRole(userId: UserId, role: ConversationRole): Future[Unit] =
    convController.setRoleInCurrentConv(userId, role)

}

object ParticipantsController {
  final case class ParticipantRequest(userId: UserId, fromDeepLink: Boolean = false)

  final case class ParticipantsFlags(isGroup:      Boolean,
                                     hasGuest:     Boolean,
                                     hasBot:       Boolean,
                                     hasExternal:  Boolean,
                                     hasFederated: Boolean)

  object ParticipantsFlags {
    val False: ParticipantsFlags = ParticipantsFlags(isGroup = false, hasGuest = false, hasBot = false, hasExternal = false, hasFederated = false)
  }
}
