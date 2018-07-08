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

import android.app.FragmentManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager.OnBackStackChangedListener
import android.support.v7.widget.Toolbar
import android.view.View.OnClickListener
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.service.tracking.GroupConversationEvent
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.CreateConversationManagerFragment._
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getColor, getDimenPx, getInt}
import com.waz.zclient.utils.RichView
import com.waz.zclient.views.DefaultPageTransitionAnimation
import com.waz.zclient.{FragmentHelper, R}

class CreateConversationManagerFragment extends FragmentHelper {

  implicit private def ctx = getContext

  private lazy val ctrl      = inject[CreateConversationController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val keyboard               = inject[KeyboardController]
  private lazy val themeController        = inject[ThemeController]

  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.getColor)

  private lazy val currentPage = Signal[Int]()

  lazy val confButtonText = for {
    currentPage <- currentPage
    users       <- ctrl.users
  } yield currentPage match {
    case SettingsPage                 => R.string.next_button
    case PickerPage if users.nonEmpty => R.string.done_button
    case PickerPage                   => R.string.skip_button
  }

  lazy val confButtonEnabled = for {
    currentPage <- currentPage
    name        <- ctrl.name
  } yield currentPage match {
    case SettingsPage if name.trim.isEmpty  => false
    case _ => true
  }

  lazy val confButtonColor = confButtonEnabled.flatMap {
    case false => Signal.const(getColor(R.color.teams_inactive_button))
    case _     => accentColor
  }

  private lazy val headerText = for {
    currentPage <- currentPage
    userCount   <- ctrl.users.map(_.size)
  } yield currentPage match {
    case SettingsPage                 => getString(R.string.new_group_header)
    case PickerPage if userCount == 0 => getString(R.string.add_people_empty_header)
    case PickerPage                   => getString(R.string.add_people_count_header, userCount.toString)
  }

  private lazy val toolbar = returning(view[Toolbar](R.id.toolbar)) { vh =>
    Signal(currentPage, themeController.darkThemeSet).map {
      case (PickerPage, true)   => R.drawable.action_back_light
      case (PickerPage, false)   => R.drawable.action_back_dark
      case (SettingsPage, false) => R.drawable.ic_action_close_dark
      case (SettingsPage, true) => R.drawable.ic_action_close_light
      case _ => R.drawable.ic_action_close_dark
    }.onUi(dr => vh.foreach(_.setNavigationIcon(dr)))
  }

  private lazy val confButton = returning(view[TypefaceTextView](R.id.confirmation_button)) { vh =>
    confButtonEnabled.onUi(e => vh.foreach(_.setEnabled(e)))
    confButtonText.onUi (id => vh.foreach(_.setText(id)))
    confButtonColor.onUi(c => vh.foreach(_.setTextColor(c)))
  }

  private lazy val header = returning(view[TypefaceTextView](R.id.header)) { vh =>
    headerText.onUi(txt => vh.foreach(_.setText(txt)))
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (enter)
      new DefaultPageTransitionAnimation(0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_long),
        getInt(R.integer.framework_animation_duration_medium),
        1f)
    else
      new DefaultPageTransitionAnimation(
        0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_medium),
        0,
        1f)
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
    inflater.inflate(R.layout.create_conv_fragment, container, false)

  override def onViewCreated(v: View, savedInstanceState: Bundle): Unit = {
    openFragment(new CreateConversationSettingsFragment, CreateConversationSettingsFragment.Tag)

    confButton.foreach(_.onClick {
      currentPage.currentValue.foreach {
        case SettingsPage =>
          keyboard.hideKeyboardIfVisible()
          openFragment(new AddParticipantsFragment, AddParticipantsFragment.Tag)
        case PickerPage =>
          ctrl.createConversation().flatMap { convId =>
            close()
            conversationController.selectConv(Some(convId), ConversationChangeRequester.START_CONVERSATION)
          } (Threading.Ui)

        case _ =>
      }

    })

    toolbar.foreach(_.setNavigationOnClickListener(new OnClickListener() {
      override def onClick(v: View): Unit =
        currentPage.currentValue.foreach {
          case SettingsPage => close()
          case PickerPage => back()
          case _ =>
        }
    }))

    //lazy init
    header
  }

  override def onDestroyView() = {
    toolbar.foreach(_.setNavigationOnClickListener(null))
    super.onDestroyView()
  }

  private def openFragment(fragment: Fragment, tag: String): Unit = {
    getChildFragmentManager.beginTransaction()
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
      .replace(R.id.container, fragment)
      .addToBackStack(tag)
      .commit()
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
}

object CreateConversationManagerFragment {

  def newInstance: CreateConversationManagerFragment = new CreateConversationManagerFragment

  val Tag = ZLog.ImplicitTag.implicitLogTag

  val SettingsPage = 0
  val PickerPage = 1
}
