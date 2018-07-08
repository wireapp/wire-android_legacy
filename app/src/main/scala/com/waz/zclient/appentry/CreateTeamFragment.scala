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
import android.support.v4.app.Fragment
import android.view.View.OnLayoutChangeListener
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.service.AccountsService
import com.waz.utils.returning
import com.waz.zclient.appentry.CreateTeamFragment._
import com.waz.zclient.appentry.controllers.CreateTeamController
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.utils.ContextUtils.getStatusBarHeight
import com.waz.zclient.{FragmentHelper, R}

trait CreateTeamFragment extends FragmentHelper {

  protected def activity = getActivity.asInstanceOf[AppEntryActivity]
  implicit def context: Context = activity

  protected lazy val createTeamController = inject[CreateTeamController]
  protected lazy val accountsService    = inject[AccountsService]

  val layoutId: Int

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(layoutId, container, false)) { v =>
      container.setBackgroundResource(R.color.teams_background)
      setKeyboardAnimation(v.asInstanceOf[ViewGroup])
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

  protected def showFragment(f: => Fragment, tag: String): Unit = activity.showFragment(f, tag)

  override def onBackPressed(): Boolean =
    if (getFragmentManager.getBackStackEntryCount > 1) {
      getFragmentManager.popBackStack()
      true
    } else {
      false
    }
}

object CreateTeamFragment {
  protected var lastKeyboardHeight = 0
}
