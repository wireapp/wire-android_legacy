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
import android.view.animation.{AlphaAnimation, Animation}
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
import com.waz.zclient.core.stores.connect.IConnectStore
import com.waz.zclient.integrations.IntegrationDetailsController
import com.waz.zclient.pages.main.connect.{BlockedUserProfileFragment, ConnectRequestLoadMode, PendingConnectRequestFragment, SendConnectRequestFragment}
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.{ParticipantsAdapter, ParticipantsController}
import com.waz.zclient.ui.text.GlyphTextView
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
  private lazy val pickUserController           = inject[IPickUserController]

  private lazy val participantsView = view[RecyclerView](R.id.pgv__participants)
  private lazy val footerMenu = returning(view[FooterMenu](R.id.fm__participants__footer)) { fm =>
    val showAddPeople = for {
      conv    <- participantsController.conv
      isGroup <- participantsController.isGroup
    } yield conv.isActive && isGroup && userAccountsController.hasAddConversationMemberPermission(conv.id)

    showAddPeople.map {
      case true  => R.string.glyph__add_people
      case false => R.string.empty_string
    }.onUi { textId =>
      fm.foreach(_.setLeftActionText(getString(textId)))
    }

    showAddPeople.map {
      case true  => R.string.conversation__action__add_people
      case false => R.string.empty_string
    }.onUi { textId =>
      fm.foreach(_.setLeftActionLabelText(getString(textId)))
    }
  }
  private lazy val emptyListIcon = view[GlyphTextView](R.id.empty_group_watermark)

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

      showNavigationIcon(true)
    }

    adapter.users.map(_.isEmpty).map {
      case true => View.VISIBLE
      case false => View.GONE
    }.onUi(emptyListIcon.setVisibility(_))
  }

  private def showNavigationIcon(isVisible: Boolean) = getParentFragment match {
    case f: ParticipantFragment => f.setNavigationIconVisible(isVisible)
    case _ =>
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

      showNavigationIcon(true)
    }

    KeyboardUtils.hideKeyboard(getActivity)
    participantsController.getUser(userId).foreach {
      case Some(user) if user.connection == ACCEPTED || userAccountsController.isTeamAccount && userAccountsController.isTeamMember(userId) =>
        participantsController.selectParticipant(userId)
        openUserProfileFragment(SingleParticipantFragment.newInstance(), SingleParticipantFragment.Tag)

      case Some(user) if user.connection == PENDING_FROM_OTHER || user.connection == PENDING_FROM_USER || user.connection == IGNORED =>
        import PendingConnectRequestFragment._
        openUserProfileFragment(newInstance(userId.str, null, ConnectRequestLoadMode.LOAD_BY_USER_ID, IConnectStore.UserRequester.PARTICIPANTS), TAG)

      case Some(user) if user.connection == BLOCKED =>
        import BlockedUserProfileFragment._
        openUserProfileFragment(newInstance(userId.str, IConnectStore.UserRequester.PARTICIPANTS), Tag)

      case Some(user) if user.connection == CANCELLED || user.connection == UNCONNECTED =>
        import SendConnectRequestFragment._
        openUserProfileFragment(newInstance(userId.str, IConnectStore.UserRequester.PARTICIPANTS), TAG)
      case _ =>
    }
  }

  // This is a workaround for the bug where child fragments disappear when
  // the parent is removed (as all children are first removed from the parent)
  // See https://code.google.com/p/android/issues/detail?id=55228
  // Apply the workaround only if this is a child fragment, and the parent is being removed.
  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation =
    Option(getParentFragment) match {
      case Some(parent) if !enter && parent.isRemoving =>
        returning(new AlphaAnimation(1, 1)) {
          _.setDuration(ViewUtils.getNextAnimationDuration(parent))
        }
      case _ => super.onCreateAnimation(transit, enter, nextAnim)
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

    showNavigationIcon(false)
  }

  override def onResume() = {
    super.onResume()
    footerMenu.foreach(_.setCallback(new FooterMenuCallback() {
      override def onLeftActionClicked(): Unit = {
        (for {
          conv    <- participantsController.conv.head
          isGroup <- participantsController.isGroup.head
        } yield (conv.id, conv.isActive, isGroup)).foreach {
          case (convId, true, true) if userAccountsController.hasAddConversationMemberPermission(convId) =>
            pickUserController.showPickUser(IPickUserController.Destination.PARTICIPANTS)
          case _ =>
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
