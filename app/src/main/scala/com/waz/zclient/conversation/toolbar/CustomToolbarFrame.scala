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
package com.waz.zclient.conversation.toolbar

import android.animation.{Animator, AnimatorListenerAdapter, ObjectAnimator}
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.waz.threading.Threading
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{R, ViewHelper}


class CustomToolbarFrame(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  lazy val topToolbar = ViewUtils.getView[CustomToolbar](this, R.id.toolbar_top)
  lazy val bottomToolbar = ViewUtils.getView[CustomToolbar](this, R.id.toolbar_bottom)
  lazy val cursorHeight = getHeight

  lazy val topHideAnimator = getHideToolbarAnimator(topToolbar, 0 , -cursorHeight)
  lazy val bottomShowAnimator = getShowToolbarAnimator(bottomToolbar, 2 * cursorHeight, 0)

  lazy val topShowAnimator = getShowToolbarAnimator(topToolbar, -cursorHeight , 0)
  lazy val bottomHideAnimator = getHideToolbarAnimator(bottomToolbar, 0, 2 * cursorHeight)

  inflate(R.layout.view_custom_toolbar)

  topToolbar.onCursorButtonClicked.on(Threading.Ui) {
    case MoreToolbarItem =>
      showBottomBar()
    case _ =>
  }

  bottomToolbar.onCursorButtonClicked.on(Threading.Ui) {
    case MoreToolbarItem =>
      showTopBar()
    case _ =>
  }

  private def getShowToolbarAnimator(view: View, fromValue: Float, toValue: Float): ObjectAnimator = {
    val animator: ObjectAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, fromValue, toValue)
    animator.setDuration(getResources.getInteger(R.integer.wire__animation__delay__regular))
    animator.setStartDelay(getResources.getInteger(R.integer.animation_delay_short))
    animator.setInterpolator(new Expo.EaseOut)
    animator.addListener(new AnimatorListenerAdapter {
      override def onAnimationStart(animation: Animator) = {
        Option(view).foreach(_.setVisibility(View.VISIBLE))
      }
    })
    animator
  }

  private def getHideToolbarAnimator(view: View, fromValue: Float, toValue: Float): ObjectAnimator = {
    val animator: ObjectAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, fromValue, toValue)
    animator.setDuration(getResources.getInteger(R.integer.wire__animation__delay__regular))
    animator.setInterpolator(new Expo.EaseIn)
    animator.addListener(new AnimatorListenerAdapter() {
      override def onAnimationCancel(animation: Animator): Unit =  {
        Option(view).foreach(_.setVisibility(View.GONE))
      }
      override def onAnimationEnd(animation: Animator): Unit =  {
        Option(view).foreach(_.setVisibility(View.GONE))
      }
    })
    animator
  }

  def showTopBar() = {
    topShowAnimator.start()
    bottomHideAnimator.start()
  }

  def showBottomBar() = {
    topHideAnimator.start()
    bottomShowAnimator.start()
  }
}
