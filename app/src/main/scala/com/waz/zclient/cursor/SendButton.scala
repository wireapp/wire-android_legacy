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
package com.waz.zclient.cursor

import android.animation.{Animator, AnimatorListenerAdapter, ValueAnimator}
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

import scala.concurrent.duration._

class SendButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends CursorIconButton(context, attrs, defStyleAttr) { view =>
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  val fadeInDuration = getInt(R.integer.animation_duration_medium).millis

  val fadeInAnimator = returning(ValueAnimator.ofFloat(0, 1)) { anim =>
    anim.setInterpolator(new Expo.EaseOut)
    anim.setDuration(fadeInDuration.toMillis)
    anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener {
      override def onAnimationUpdate(animation: ValueAnimator): Unit = {
        view.setAlpha(animation.getAnimatedValue.asInstanceOf[java.lang.Float])
      }
    })
    anim.addListener(new AnimatorListenerAdapter {
      override def onAnimationStart(animation: Animator): Unit = {
        view.setVisible(true)
      }
    })
  }

  override val buttonColor = Signal.empty[ColorStateList] //send button color is defined in XML

  menuItem ! Some(CursorMenuItem.Send)

  override def onFinishInflate(): Unit = {
    super.onFinishInflate()

    setColor(getColor(R.color.text__primary_dark))

    controller.sendButtonVisible.onUi {
      case true =>
        fadeInAnimator.start()
      case false =>
        fadeInAnimator.cancel()
        view.setVisible(false)
        view.setAlpha(0)
    }
  }
}
