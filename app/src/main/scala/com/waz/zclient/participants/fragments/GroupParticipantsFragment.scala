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

package com.waz.zclient.participants.fragments

import android.content.Context
import android.os.Bundle
import android.support.annotation.Nullable
import android.support.v4.app.Fragment
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.NetworkMode
import com.waz.api.User.ConnectionStatus._
import com.waz.model.{UserData, UserId}
import com.waz.service.NetworkModeService
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events._
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.conversation.creation.{AddParticipantsFragment, CreateConversationController}
import com.waz.zclient.integrations.IntegrationDetailsController
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.{ParticipantsAdapter, ParticipantsController, UserRequester}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{FragmentHelper, R}

class GroupParticipantsFragment extends FragmentHelper {

  implicit def ctx: Context = getActivity
  import Threading.Implicits.Ui

  private lazy val participantsController       = inject[ParticipantsController]
  private lazy val convScreenController         = inject[IConversationScreenController]
  private lazy val userAccountsController       = inject[UserAccountsController]
  private lazy val integrationDetailsController = inject[IntegrationDetailsController]

  private lazy val participantsView = view[RecyclerView](R.id.pgv__participants)

  lazy val showAddPeople = for {
    conv    <- participantsController.conv
    isGroup <- participantsController.isGroup
    hasPerm <- userAccountsController.hasAddConversationMemberPermission(conv.id)
  } yield conv.isActive && isGroup && hasPerm

  private lazy val footerMenu = returning(view[FooterMenu](R.id.fm__participants__footer)) { fm =>
    showAddPeople.map {
      case true  => R.string.glyph__add_people
      case false => R.string.empty_string
    }.map(getString)
     .onUi(t => fm.foreach(_.setLeftActionText(t)))

    showAddPeople.map {
      case true  => R.string.conversation__action__add_people
      case false => R.string.empty_string
    }.map(getString)
     .onUi(t => fm.foreach(_.setLeftActionLabelText(t)))
  }

  private lazy val participantsAdapter = returning(new ParticipantsAdapter(getInt(R.integer.participant_column__count))) { adapter =>
    new FutureEventStream[UserId, Option[UserData]](adapter.onClick, participantsController.getUser).onUi {
      case Some(user) => (user.providerId, user.integrationId) match {
        case (Some(pId), Some(iId)) =>
          participantsController.conv.head.map { conv =>
            Option(getParentFragment) match {
              case Some(f: ParticipantFragment) =>
                integrationDetailsController.setRemoving(conv.id, user.id)
                f.showIntegrationDetails(pId, iId)
              case _ =>
            }

          }
        case _ => showUser(user.id)
      }
      case _ =>
    }

    adapter.onGuestOptionsClick.onUi { _ =>
      getFragmentManager.beginTransaction
        .setCustomAnimations(
          R.anim.fragment_animation_second_page_slide_in_from_right,
          R.anim.fragment_animation_second_page_slide_out_to_left,
          R.anim.fragment_animation_second_page_slide_in_from_left,
          R.anim.fragment_animation_second_page_slide_out_to_right)
        .replace(R.id.fl__participant__container, new GuestOptionsFragment(), GuestOptionsFragment.Tag)
        .addToBackStack(GuestOptionsFragment.Tag)
        .commit
    }
  }

  private def showUser(userId: UserId): Unit = {
    verbose(s"onShowUser($userId)")
    convScreenController.showUser(userId)
    participantsController.selectParticipant(userId)

    def openUserProfileFragment(fragment: Fragment, tag: String) = {
      getFragmentManager.beginTransaction
        .setCustomAnimations(
          R.anim.fragment_animation_second_page_slide_in_from_right,
          R.anim.fragment_animation_second_page_slide_out_to_left,
          R.anim.fragment_animation_second_page_slide_in_from_left,
          R.anim.fragment_animation_second_page_slide_out_to_right)
        .replace(R.id.fl__participant__container, fragment, tag)
        .addToBackStack(tag)
        .commit
    }

    KeyboardUtils.hideKeyboard(getActivity)

    for {
      userOpt      <- participantsController.getUser(userId)
      isTeamMember <- userAccountsController.isTeamMember(userId).head
    } userOpt match {
      case Some(user) if user.connection == ACCEPTED || user.expiresAt.isDefined || isTeamMember =>
        participantsController.selectParticipant(userId)
        openUserProfileFragment(SingleParticipantFragment.newInstance(), SingleParticipantFragment.Tag)

      case Some(user) if user.connection == PENDING_FROM_OTHER || user.connection == PENDING_FROM_USER || user.connection == IGNORED =>
        import com.waz.zclient.connect.PendingConnectRequestFragment._
        openUserProfileFragment(newInstance(userId, UserRequester.PARTICIPANTS), Tag)

      case Some(user) if user.connection == BLOCKED =>
        import BlockedUserProfileFragment._
        openUserProfileFragment(newInstance(userId.str, UserRequester.PARTICIPANTS), Tag)

      case Some(user) if user.connection == CANCELLED || user.connection == UNCONNECTED =>
        import com.waz.zclient.connect.SendConnectRequestFragment._
        openUserProfileFragment(newInstance(userId.str, UserRequester.PARTICIPANTS), Tag)
      case _ =>
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_group_participant, viewGroup, false)

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    participantsView.foreach { v =>
      v.setAdapter(participantsAdapter)
      v.setLayoutManager(new LinearLayoutManager(getActivity))
    }

    participantsView
    footerMenu.foreach(_.setRightActionText(getString(R.string.glyph__more)))
  }

  override def onResume() = {
    super.onResume()
    footerMenu.foreach(_.setCallback(new FooterMenuCallback() {
      override def onLeftActionClicked(): Unit = {
        showAddPeople.head.map {
          case true =>
            participantsController.conv.head.foreach { conv =>
              inject[CreateConversationController].setAddToConversation(conv.id)
              getFragmentManager.beginTransaction
                .setCustomAnimations(
                  R.anim.in_from_bottom_enter,
                  R.anim.out_to_bottom_exit,
                  R.anim.in_from_bottom_pop_enter,
                  R.anim.out_to_bottom_pop_exit)
                .replace(R.id.fl__participant__container, new AddParticipantsFragment, AddParticipantsFragment.Tag)
                .addToBackStack(AddParticipantsFragment.Tag)
                .commit
            }
          case _ => //
        }
      }

      override def onRightActionClicked(): Unit = {
        inject[NetworkModeService].networkMode.head.map {
          case NetworkMode.OFFLINE =>
            ViewUtils.showAlertDialog(
              getActivity,
              R.string.alert_dialog__no_network__header,
              R.string.leave_conversation_failed__message, //TODO - message doesn't match action
              R.string.alert_dialog__confirmation, null, true
            )
          case _ =>
            participantsController.conv.head.foreach { conv =>
              if (conv.isActive)
                convScreenController.showConversationMenu(false, conv.id)
            }
        }
      }
    }))
  }

  override def onPause() = {
    footerMenu.foreach(_.setCallback(null))
    super.onPause()
  }

  override def onBackPressed(): Boolean = {
    super.onBackPressed()
    participantsAdapter.onBackPressed()
  }

}

object GroupParticipantsFragment {
  val Tag: String = classOf[GroupParticipantsFragment].getName

  def newInstance(): GroupParticipantsFragment = new GroupParticipantsFragment
}
