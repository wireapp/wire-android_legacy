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
package com.waz.zclient.appentry

import android.content.Context
import android.os.Bundle
import android.view.View.OnLayoutChangeListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.FrameLayout
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.utils.returning
import com.waz.zclient.appentry.CreateTeamFragment._
import com.waz.zclient.appentry.controllers.AppEntryController._
import com.waz.zclient.appentry.controllers.{AppEntryController, InvitationsController}
import com.waz.zclient.appentry.fragments.SignInFragment
import com.waz.zclient.appentry.scenes.FirstScreenViewHolder
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils.getStatusBarHeight
import com.waz.zclient.utils.{ContextUtils, RichView}
import com.waz.zclient.{FragmentHelper, R}

class CreateTeamFragment extends BaseFragment[Container] with FragmentHelper {

  def activity = getActivity.asInstanceOf[AppEntryActivity]
  implicit def context: Context = activity

  private lazy val appEntryController = inject[AppEntryController]
  private lazy val accountsService    = inject[AccountsService]
  private lazy val invitesController  = inject[InvitationsController]

  private var previousStage = Option.empty[AppEntryStage]
  private var lastKeyboardHeight = 0

  lazy val container = returning(view[FrameLayout](R.id.container)) { vh =>
    vh.onClick(_ => getContainer.abortAddAccount())
  }

  lazy val closeButton = returning(view[GlyphTextView](R.id.close_button)) { vh =>
    //!Set(SetUsernameTeam, TeamSetPicture, InviteToTeam, EnterAppStage) TODO set invisible in these states?
    accountsService.zmsInstances.map(_.nonEmpty).onUi(vis => vh.foreach(_.setVisible(vis)))
  }
  lazy val skipButton = returning(view[TypefaceTextView](R.id.skip_button)) { vh =>
    //TODO set invisible until invitations page
    vh.onClick(_ => appEntryController.skipInvitations())
    invitesController.invitations.map(_.isEmpty).map {
      case true => R.string.teams_invitations_skip
      case false => R.string.teams_invitations_done
    }.onUi(t => vh.foreach(_.setText(t)))
  }
  lazy val toolbar = returning(view[FrameLayout](R.id.teams_toolbar)) { vh =>
    //TODO set invisible on invitations page
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.app_entry_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    //TODO

//    appEntryController.entryStage.onUi { state =>
//      implicit val ctx: Context = getContext
//
//      val viewHolder = state match {
//        case NoAccountState(FirstScreen)        => FirstScreenViewHolder(inflator.inflate(R.layout.app_entry_scene, null))
//        case NoAccountState(RegisterTeamScreen) => TeamNameViewHolder(inflator.inflate(R.layout.create_team_name_scene, null))
//        case SetTeamEmail                       => SetEmailViewHolder(inflator.inflate(R.layout.set_email_scene, null))
//        case VerifyTeamEmail                    => VerifyEmailViewHolder(inflator.inflate(R.layout.verify_email_scene, null))
//        case SetUsersNameTeam                   => SetNameViewHolder(inflator.inflate(R.layout.set_name_scene, null))
//        case SetPasswordTeam                    => SetPasswordViewHolder(inflator.inflate(R.layout.set_password_scene, null))
//        case SetUsernameTeam                    => SetUsernameViewHolder(inflator.inflate(R.layout.set_username_scene, null))
//        case InviteToTeam                       => InviteToTeamViewHolder(inflator.inflate(R.layout.invite_team_scene, null))
//        case _                                  => EmptyViewHolder(new View(getContext))
//      }
//


//    val forward = previousStage.fold(true)(_.depth < state.depth)
//    val sameDepth = previousStage.fold(false)(_.depth == state.depth)
//    val transition = DefaultTransition()
//
//    val previousViews = (0 until container.getChildCount).map(container.getChildAt)
//    previousViews.foreach { pv =>
//      if (!sameDepth)
//        transition.outAnimation(pv, container, forward = forward).withEndAction(new Runnable {
//          override def run(): Unit = container.removeView(pv)
//        }).start()
//      else
//        container.removeView(pv)
//    }
//
//    if (previousViews.nonEmpty && !sameDepth)
//      transition.inAnimation(viewHolder.root, container, forward = forward).start()


//    previousStage = Some(state)

//    if (state != NoAccountState(FirstScreen) && viewHolder.root.isInstanceOf[ViewGroup]) {
//      setKeyboardAnimation(viewHolder.root.asInstanceOf[ViewGroup])
//    }
//    }

    val inflator = LayoutInflater.from(getActivity)
    val viewHolder = FirstScreenViewHolder(inflator.inflate(R.layout.app_entry_scene, null))
    viewHolder.loginButton.onClick(activity.showFragment(SignInFragment.newInstance(), SignInFragment.Tag))
    container.addView(viewHolder.root)
    viewHolder.onCreate()
  }

  def setKeyboardAnimation(view: ViewGroup): Unit = {
    view.getLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    view.addOnLayoutChangeListener(new OnLayoutChangeListener {
      override def onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int): Unit = {
        val softKeysHeight = KeyboardUtils.getSoftButtonsBarHeight(getActivity)
        val kHeight = KeyboardUtils.getKeyboardHeight(view)
        val padding = ContextUtils.getDimenPx(R.dimen.app_entry_keyboard_content_padding)
        val screenHeight = getContext.getResources.getDisplayMetrics.heightPixels - getStatusBarHeight - softKeysHeight

        if (v.getTranslationY == 0 && lastKeyboardHeight > 0) {
          v.setTranslationY(screenHeight - lastKeyboardHeight - padding - v.getHeight + 2 * softKeysHeight)
        } else if (v.getTranslationY == 0) {
          v.setTranslationY(screenHeight / 2 - v.getHeight / 2)
        } else if (kHeight - softKeysHeight > 0) {
          lastKeyboardHeight = kHeight
          v.animate()
            .translationY(screenHeight - kHeight - padding - v.getHeight + 2 * softKeysHeight)
            .setDuration(ContextUtils.getInt(R.integer.wire__animation__delay__short))
        } else {
          v.animate()
            .translationY(if (screenHeight > v.getHeight) screenHeight / 2 - v.getHeight / 2 else screenHeight - v.getHeight)
            .setDuration(ContextUtils.getInt(R.integer.wire__animation__delay__short))
        }
      }
    })
  }

  override def onBackPressed(): Boolean = {
//    if (appEntryController.entryStage.currentValue.exists(_.isInstanceOf[NoAccountState]) && ZMessaging.currentAccounts.loggedInAccounts.currentValue.exists(_.nonEmpty))
//      false
//    else if (appEntryController.entryStage.currentValue.exists(_ != NoAccountState(FirstScreen))) {
//      appEntryController.createTeamBack()
//      true
//    } else
      false
  }
}

object CreateTeamFragment {
  val TAG: String = classOf[CreateTeamFragment].getName

  def newInstance = new CreateTeamFragment

  trait Container {
    def abortAddAccount(): Unit
  }
}
