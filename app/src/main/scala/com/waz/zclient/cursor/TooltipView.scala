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

import android.content.Context
import android.util.AttributeSet
import android.animation.{Animator, AnimatorListenerAdapter, ValueAnimator}
import com.waz.utils.returning
import com.waz.zclient.ViewHelper
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.R
import com.waz.zclient.utils._
import com.waz.utils._
import ContextUtils._
import android.view.View
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import org.threeten.bp.Instant
import com.waz.ZLog.ImplicitTag._

import concurrent.duration._

class TooltipView(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends TypefaceTextView(context, attrs, defStyleAttr) with ViewHelper { view =>
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  import TooltipView._
  import com.waz.threading.Threading.Implicits.Ui

  val controller = inject[CursorController]

  val tooltip = Signal.wrap(controller.onShowTooltip.map { case (msg, anchor) => (msg, anchor, Instant.now) })

  val visible = tooltip.map(_._3).orElse(Signal const Instant.EPOCH).flatMap {
    case time if time <= Instant.now - TooltipDuration => Signal const false
    case time =>
      val delay = Instant.now.until(time + TooltipDuration).asScala
      Signal.future(CancellableFuture.delayed(delay) { false }).orElse(Signal const true)
  }

  val width = Signal[Int]()

  val anchorPosition = tooltip map {
    case (_, anchor, _) => anchor.getX + anchor.getWidth / 2
  }

  val text = tooltip.map(_._1.resTooltip)

  val margin = getDimenPx(R.dimen.wire__padding__regular)

  val offset = Signal(anchorPosition, width, controller.cursorWidth) map {
    case (anchor, w, cursorWidth) =>
      math.max(margin, math.min(cursorWidth - margin - w, anchor - w /2))
  }

  val fadeAnimator = returning(ValueAnimator.ofFloat(0, 1)) { anim =>
    anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener {
      override def onAnimationUpdate(animation: ValueAnimator): Unit =
        setAlpha(animation.getAnimatedValue.asInstanceOf[java.lang.Float])
    })

    anim.addListener(new AnimatorListenerAdapter {
      override def onAnimationStart(animation: Animator): Unit = view.setVisible(true)
      override def onAnimationEnd(animation: Animator): Unit =
        if (visible.currentValue.contains(false)) {
          view.setVisible(false)
        }
    })
  }

  override def onFinishInflate(): Unit = {
    super.onFinishInflate()

    view.setVisible(false)
    view.setAlpha(0)

    visible.on(Threading.Ui) {
      case true => fadeAnimator.start()
      case false =>
        if (view.getVisibility == View.VISIBLE) {
          fadeAnimator.reverse()
        }
    }

    offset.on(Threading.Ui) { view.setTranslationX }

    text.on(Threading.Ui) { view.setText }
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    width ! right - left
  }
}

object TooltipView {
  private val TooltipDuration = 1500.millis
}
