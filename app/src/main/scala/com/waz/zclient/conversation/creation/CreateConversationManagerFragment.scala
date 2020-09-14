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
package com.waz.zclient.conversation.creation

import androidx.fragment.app.FragmentManager
import android.os.Bundle
import androidx.fragment.app.FragmentManager.OnBackStackChangedListener
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.annotation.IdRes
import com.waz.service.tracking.GroupConversationEvent
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.CreateConversationManagerFragment._
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.pages.NoOpContainer
import com.waz.zclient.ui.DefaultToolbarFragment
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{FragmentHelper, R, SpinnerController}
import com.waz.threading.Threading._

class CreateConversationManagerFragment extends DefaultToolbarFragment[NoOpContainer] with FragmentHelper {

  implicit private def ctx = getContext
  import Threading.Implicits.Ui

  private lazy val ctrl                   = inject[CreateConversationController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val keyboard               = inject[KeyboardController]
  private lazy val themeController        = inject[ThemeController]
  private lazy val accentColor            = inject[AccentColorController].accentColor.map(_.color)
  private lazy val spinner                = inject[SpinnerController]

  private lazy val currentPage = Signal[Int]()

  private lazy val confButtonText = for {
    currentPage <- currentPage
    users       <- ctrl.users
    integrations <- ctrl.integrations
  } yield currentPage match {
    case SettingsPage                 => R.string.next_button
    case PickerPage if users.nonEmpty || integrations.nonEmpty => R.string.done_button
    case PickerPage                   => R.string.skip_button
  }

  private val convCreationInProgress = Signal(false)

  private lazy val confButtonEnabled = for {
    currentPage       <- currentPage
    name              <- ctrl.name
    memberCount       <- ctrl.users.map(_.size)
    integrationsCount <- ctrl.integrations.map(_.size)
    inProgress        <- convCreationInProgress
  } yield {
    if (inProgress) false else currentPage match {
      case SettingsPage if name.trim.isEmpty  => false
      case _ if memberCount + integrationsCount >= ConversationController.MaxParticipants => false
      case _ => true
    }
  }

  private lazy val headerText = for {
    currentPage <- currentPage
    userCount   <- ctrl.users.map(_.size)
    integrationsCount <- ctrl.integrations.map(_.size)
  } yield currentPage match {
    case SettingsPage                 => getString(R.string.new_group_header)
    case PickerPage if userCount == 0 && integrationsCount == 0 => getString(R.string.add_participants_empty_header)
    case PickerPage                   => getString(R.string.add_participants_count_header, (userCount + integrationsCount).toString)
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    ctrl.onShowCreateConversation.onUi {
      case false =>
        ctrl.fromScreen.head.map {
          case GroupConversationEvent.StartUi => getFragmentManager.popBackStack(Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
          case _ =>
        } (Threading.Background)
      case _ =>
    }

    ctrl.users.zip(ctrl.integrations).map { case (users, integrations) => users.size + integrations.size >= ConversationController.MaxParticipants }.onUi{
      case true =>
        ViewUtils.showAlertDialog(getContext,
          getString(R.string.max_participants_alert_title),
          getString(R.string.max_participants_create_alert_message, ConversationController.MaxParticipants.toString),
          getString(android.R.string.ok),
          null,
          true)
      case _=>
    }

    getChildFragmentManager.addOnBackStackChangedListener(new OnBackStackChangedListener {
      override def onBackStackChanged(): Unit =
        if (getChildFragmentManager.getBackStackEntryCount  > 0) {
          val fragment = getChildFragmentManager.getBackStackEntryAt(getChildFragmentManager.getBackStackEntryCount - 1)
          currentPage ! (fragment.getName match {
            case CreateConversationSettingsFragment.Tag => SettingsPage
            case AddParticipantsFragment.Tag => PickerPage
            case _ => SettingsPage
          })
        }
    })
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_create_conversation_manager, container, false)

  override def onViewCreated(v: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)
    openFragmentWithAnimation(
      R.id.fragment_create_conversation_manager_layout_container,
      new CreateConversationSettingsFragment, CreateConversationSettingsFragment.Tag)

    Signal.zip(currentPage, themeController.darkThemeSet).map {
      case (PickerPage, true) => R.drawable.action_back_light
      case (PickerPage, false) => R.drawable.action_back_dark
      case (SettingsPage, false) => R.drawable.ic_action_close_dark
      case (SettingsPage, true) => R.drawable.ic_action_close_light
      case _ => R.drawable.ic_action_close_dark
    }.onUi(drawable => toolbar.foreach(_.setNavigationIcon(drawable)))

    headerText.onUi(setTitle)
    confButtonEnabled.onUi(setActionButtonEnabled)
    confButtonText.onUi(text => setActionButtonText(getString(text)))
  }

  private def close() = {
    keyboard.hideKeyboardIfVisible()
    ctrl.onShowCreateConversation ! false
  }

  private def back(): Unit = {
    keyboard.hideKeyboardIfVisible()
    getChildFragmentManager.popBackStack()
  }

  override def onBackPressed(): Boolean = {
    keyboard.hideKeyboardIfVisible() || {
      currentPage.currentValue.foreach {
        case SettingsPage => close()
        case PickerPage => back()
        case _ =>
      }
      true
    }
  }

  override def onNavigationClick(): Unit = {
    currentPage.currentValue.foreach {
      case SettingsPage => close()
      case PickerPage => back()
      case _ =>
    }
  }

  override def onActionClick(): Unit = {
    currentPage.currentValue.foreach {
      case SettingsPage =>
        keyboard.hideKeyboardIfVisible()
        openFragmentWithAnimation(
          R.id.fragment_create_conversation_manager_layout_container,
          new AddParticipantsFragment, AddParticipantsFragment.Tag)
      case PickerPage =>
        convCreationInProgress.head.foreach {
          case false =>
            convCreationInProgress ! true
            spinner.showSpinner(true)
            ctrl.createConversation().foreach { convId =>
              spinner.hideSpinner()
              close()
              conversationController
                .selectConv(Some(convId), ConversationChangeRequester.START_CONVERSATION)
                .onComplete(_ => convCreationInProgress ! false)
            }
          case true =>
        }
      case _ =>
    }
  }

  @IdRes
  override protected def getToolbarId = R.id.fragment_create_conversation_manager_toolbar
}

object CreateConversationManagerFragment {

  def newInstance: CreateConversationManagerFragment = new CreateConversationManagerFragment

  val Tag: String = getClass.getSimpleName

  val SettingsPage = 0
  val PickerPage = 1
}
