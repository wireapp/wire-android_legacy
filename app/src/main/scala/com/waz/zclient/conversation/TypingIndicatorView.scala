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
package com.waz.zclient.conversation

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, TextView}
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal

import com.waz.zclient.{R, ViewHelper}

class TypingIndicatorView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  val zms = inject[Signal[ZMessaging]]
  lazy val convController = inject[ConversationController]
  inflate(R.layout.typing_indicator)

  val nameTextView = findById[TextView](R.id.ttv__typing_indicator_names)
  val dotsView = findById[View](R.id.gtv__is_typing_dots)
  val penView = findById[View](R.id.gtv__is_typing_pen)

  private var animationRunning: Boolean = false
  private val handler = new Handler

  lazy val typingUsers = for {
    z <- zms
    convId <- convController.currentConvId
    userIds <- z.typing.typingUsers(convId)
    users <- z.usersStorage.listSignal(userIds.filterNot(_ == z.selfUserId))
  } yield users

  typingUsers.onUi { users =>
    if (users.isEmpty) {
      nameTextView.setText("")
      setVisibility(View.GONE)
      endAnimation()
    } else {
      nameTextView.setText(users.map(_.getDisplayName).mkString(", "))
      setVisibility(View.VISIBLE)
      startAnimation()
    }
  }

  private def startAnimation() =
    animationRunning match {
      case true => Unit
      case false =>
        animationRunning = true
        runAnimation()
    }

  private def runAnimation(): Unit =
    if (animationRunning) {
      val stepDuration = getResources.getInteger(R.integer.animation_duration_medium_rare)
      val step1 = dotsView.getWidth / 3
      val step2 = step1 * 2
      val step3 = dotsView.getWidth

      def animateStep(step: Int) =
        penView.animate().translationX(step).setDuration(stepDuration).start()

      def getRunnable(step: Int) = new Runnable {
        override def run(): Unit = animateStep(step)
      }

      animateStep(step1)
      handler.postDelayed(getRunnable(step2), stepDuration * 2)

      handler.postDelayed(getRunnable(step3), stepDuration * 4)

      handler.postDelayed(new Runnable() {
        override def run(): Unit = {
          runAnimation()
        }
      }, stepDuration * 8)
    }

  private def endAnimation() =
    if (animationRunning) {
      handler.removeCallbacksAndMessages(null)
      animationRunning = false
    }
}
