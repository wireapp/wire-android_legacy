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

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager.OnBackStackChangedListener
import android.support.v7.widget.Toolbar
import android.view.View.OnClickListener
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.threading.Threading
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.NewConversationFragment._
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getDimenPx, getInt}
import com.waz.zclient.utils.RichView
import com.waz.zclient.views.DefaultPageTransitionAnimation
import com.waz.zclient.{FragmentHelper, R}

class NewConversationFragment extends Fragment with FragmentHelper {

  implicit private def ctx = getContext

  private lazy val newConvController = inject[NewConversationController]
  private lazy val conversationController = inject[ConversationController]

  private lazy val nextButton = view[TypefaceTextView](R.id.next_button)
  private lazy val toolbar = view[Toolbar](R.id.toolbar)

  private lazy val currentPage: SourceSignal[Int] = Signal()

  private lazy val buttonState = for {
    currentPage <- currentPage
    name <- newConvController.name
    users <- newConvController.users
  } yield currentPage match {
    case SettingsPage if name.nonEmpty => (true, R.string.next_button)
    case SettingsPage => (false, R.string.next_button)
    case PickerPage if users.nonEmpty => (true, R.string.create_button)
    case PickerPage => (true, R.string.skip_button)
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
    newConvController.reset()

    buttonState.onUi{ case (enabled, textId) =>
      nextButton.foreach { btn =>
        btn.setEnabled(enabled)
        btn.setText(textId)
      }
    }

    getChildFragmentManager.addOnBackStackChangedListener(new OnBackStackChangedListener {
      override def onBackStackChanged(): Unit =
        if (getChildFragmentManager.getBackStackEntryCount  > 0) {
          val fragment = getChildFragmentManager.getBackStackEntryAt(getChildFragmentManager.getBackStackEntryCount - 1)
          currentPage ! (fragment.getName match {
            case NewConversationSettingsFragment.Tag => SettingsPage
            case NewConversationPickFragment.Tag => PickerPage
            case _ => SettingsPage
          })
        }
    })
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.create_conv_fragment, container, false)

  override def onViewCreated(v: View, savedInstanceState: Bundle): Unit = {
    openFragment(new NewConversationSettingsFragment, NewConversationSettingsFragment.Tag)

    nextButton.foreach(_.onClick {
      currentPage.currentValue.foreach {
        case SettingsPage =>
          openFragment(new NewConversationPickFragment, NewConversationPickFragment.Tag)
        case PickerPage =>
          newConvController.createConversation().flatMap { convId =>
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

    buttonState.currentValue.foreach { case (enabled, textId) =>
      nextButton.foreach { btn =>
        btn.setEnabled(enabled)
        btn.setText(textId)
      }
    }

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

  private def close() = getFragmentManager.popBackStack()

  private def back(): Unit = getChildFragmentManager.popBackStack()
}

object NewConversationFragment {
  val Tag = ZLog.ImplicitTag.implicitLogTag

  val SettingsPage = 0
  val PickerPage = 1
}
