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

import android.content.{Context, DialogInterface}
import android.os.Bundle
import androidx.annotation.Nullable
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.api.{ErrorType, NetworkMode}
import com.waz.model.ErrorData
import com.waz.service.{IntegrationsService, NetworkModeService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.signals._
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.{AddParticipantsFragment, CreateConversationController}
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.{ParticipantsAdapter, ParticipantsController}
import com.waz.zclient.utils.ContextUtils.showToast
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{ErrorsController, FragmentHelper, R, SpinnerController}
import com.waz.threading.Threading._
import com.waz.zclient.common.controllers.BrowserController

class GroupParticipantsFragment extends FragmentHelper {

  implicit def ctx: Context = getActivity
  import Threading.Implicits.Ui

  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convScreenController   = inject[IConversationScreenController]
  private lazy val integrationsService    = inject[Signal[IntegrationsService]]
  private lazy val spinnerController      = inject[SpinnerController]
  private lazy val errorsController       = inject[ErrorsController]

  private lazy val participantsView = view[RecyclerView](R.id.pgv__participants)

  lazy val showAddParticipants: Signal[Boolean] = for {
    conv         <- participantsController.conv
    flags        <- participantsController.flags
    isGroupOrBot =  flags.isGroup || flags.hasBot
    hasPerm      <- participantsController.selfRole.map(_.canAddGroupMember)
  } yield conv.isActive && isGroupOrBot && hasPerm

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

    participantsController
      .otherParticipants
      .map(_.size + 1 < ConversationController.MaxParticipants)
      .onUi(e => fm.foreach(_.setLeftActionEnabled(e)))
  }

  private lazy val participantsAdapter = returning(new ParticipantsAdapter(participantsController.participants, Some(7))) { adapter =>
    adapter.onClick.mapSync(participantsController.getUser).onUi {
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
      participantsController.areGuestLinksEnabled.head.foreach {
        case true  => slideFragmentInFromRight(new GuestOptionsFragment(), GuestOptionsFragment.Tag)
        case false =>
      }(Threading.Ui)
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

    zms.flatMap(_.errors.getErrors).onUi { _.foreach(handleSyncError) }

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

  private def handleSyncError(err: ErrorData): Unit = err.errType match {
    case ErrorType.CANNOT_ADD_PARTICIPANT_WITH_MISSING_LEGAL_HOLD_CONSENT =>
      ViewUtils.showAlertDialog(
        ctx,
        getString(R.string.legal_hold_participant_missing_consent_alert_title),
        getString(R.string.legal_hold_participant_missing_consent_alert_message),
        getString(android.R.string.ok),
        getString(R.string.legal_hold_participant_missing_consent_alert_negative_button),
        null,
        new DialogInterface.OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int): Unit =
            inject[BrowserController].openAboutLegalHold()
        }
      )
      errorsController.dismissSyncError(err.id)
    case _ =>
      ()
  }

}

object GroupParticipantsFragment {
  val Tag: String = classOf[GroupParticipantsFragment].getName

  def newInstance(): GroupParticipantsFragment = new GroupParticipantsFragment
}
