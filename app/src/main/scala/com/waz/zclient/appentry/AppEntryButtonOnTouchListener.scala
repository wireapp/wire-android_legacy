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

import android.animation.Animator
import android.content.Context
import android.graphics.Rect
import android.view.View.OnTouchListener
import android.view.{MotionEvent, View, ViewPropertyAnimator}
import com.waz.zclient.R

case class AppEntryButtonOnTouchListener(onClick: () => Unit)(implicit context: Context) extends OnTouchListener {

  private var finished = false

  def isOutside(v: View, event: MotionEvent): Boolean ={
    val rect = new Rect(v.getLeft, v.getTop, v.getRight, v.getBottom)
    !rect.contains(v.getLeft + event.getX.toInt, v.getTop + event.getY.toInt)
  }

  def animatePress(v: View): ViewPropertyAnimator =
    v.animate()
      .scaleX(0.96f)
      .scaleY(0.96f)
      .setDuration(context.getResources.getInteger(R.integer.wire__animation__delay__short))

  def animateRelease(v: View): ViewPropertyAnimator =
    v.animate()
      .scaleX(1f)
      .scaleY(1f)
      .setDuration(context.getResources.getInteger(R.integer.wire__animation__delay__short))


  override def onTouch(v: View, event: MotionEvent): Boolean = {
    event.getAction match {
      case MotionEvent.ACTION_DOWN =>
        animatePress(v)
        finished = false
      case MotionEvent.ACTION_MOVE if isOutside(v, event) && !finished =>
        animateRelease(v).start()
        finished = true
      case MotionEvent.ACTION_OUTSIDE | MotionEvent.ACTION_HOVER_EXIT | MotionEvent.ACTION_CANCEL if !finished =>
        animateRelease(v).start()
        finished = true
      case MotionEvent.ACTION_UP if !finished =>
        finished = true
        animateRelease(v).setListener(new Animator.AnimatorListener {
          override def onAnimationEnd(animation: Animator): Unit = onClick()
          override def onAnimationCancel(animation: Animator): Unit = {}
          override def onAnimationRepeat(animation: Animator): Unit = {}
          override def onAnimationStart(animation: Animator): Unit = {}
        }).start()
      case _ =>
    }
    false
  }
}
