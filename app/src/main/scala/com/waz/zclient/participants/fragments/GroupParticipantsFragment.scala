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

package com.waz.zclient.participants.fragments

import android.content.Context
import android.os.Bundle
import androidx.annotation.Nullable
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.api.NetworkMode
import com.waz.model.{UserData, UserId}
import com.waz.service.{IntegrationsService, NetworkModeService}
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events._
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.{AddParticipantsFragment, CreateConversationController}
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.{ParticipantsAdapter, ParticipantsController}
import com.waz.zclient.utils.ContextUtils.showToast
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{FragmentHelper, R, SpinnerController}

class GroupParticipantsFragment extends FragmentHelper {

  implicit def ctx: Context = getActivity
  import Threading.Implicits.Ui

  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convScreenController   = inject[IConversationScreenController]
  private lazy val integrationsService    = inject[Signal[IntegrationsService]]
  private lazy val spinnerController      = inject[SpinnerController]

  private lazy val participantsView = view[RecyclerView](R.id.pgv__participants)

  lazy val showAddParticipants = for {
    conv         <- participantsController.conv
    isGroupOrBot <- participantsController.isGroupOrBot
    hasPerm      <- participantsController.selfRole.map(_.canAddGroupMember)
  } yield conv.isActive && isGroupOrBot && hasPerm

  lazy val shouldEnableAddParticipants = participantsController.otherParticipants.map(_.size + 1 < ConversationController.MaxParticipants)

  private lazy val footerMenu = returning(view[FooterMenu](R.id.fm__participants__footer)) { fm =>
    showAddParticipants.map {
      case true  => R.string.glyph__plus
      case false => R.string.empty_string
    }.map(getString)
     .onUi(t => fm.foreach(_.setLeftActionText(t)))

    showAddParticipants.map {
      case true  => R.string.conversation__action__add_participants
      case false => R.string.empty_string
    }.map(getString)
     .onUi(t => fm.foreach(_.setLeftActionLabelText(t)))

    shouldEnableAddParticipants.onUi(e => fm.foreach(_.setLeftActionEnabled(e)))
  }

  private lazy val participantsAdapter = returning(new ParticipantsAdapter(participantsController.participants, Some(7))) { adapter =>
    new FutureEventStream[UserId, Option[UserData]](adapter.onClick, participantsController.getUser).onUi {
      case Some(user) => (user.providerId, user.integrationId) match {
        case (Some(pId), Some(iId)) =>
          for {
            conv <- participantsController.conv.head
            _    =  spinnerController.showSpinner()
            resp <- integrationsService.head.flatMap(_.getIntegration(pId, iId))
          } {
            spinnerController.hideSpinner()
            resp match {
              case Right(service) =>
                Option(getParentFragment) match {
                  case Some(f: ParticipantFragment) =>
                    f.showIntegrationDetails(service, conv.id, user.id)
                  case _ =>
                }
              case Left(err) =>
                showToast(R.string.generic_error_header)
            }
          }
        case _ => participantsController.onShowUser ! Some(user.id)
      }
      case _ =>
    }

    adapter.onGuestOptionsClick.onUi { _ =>
      slideFragmentInFromRight(new GuestOptionsFragment(), GuestOptionsFragment.Tag)
    }

    adapter.onEphemeralOptionsClick.onUi { _ =>
      slideFragmentInFromRight(new EphemeralOptionsFragment(), EphemeralOptionsFragment.Tag)
    }

    adapter.onNotificationsClick.onUi { _ =>
      slideFragmentInFromRight(new NotificationsOptionsFragment(), NotificationsOptionsFragment.Tag)
    }

    adapter.onShowAllParticipantsClick.onUi { _ =>
      slideFragmentInFromRight(new AllGroupParticipantsFragment(), AllGroupParticipantsFragment.Tag)
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
        showAddParticipants.head.map {
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
