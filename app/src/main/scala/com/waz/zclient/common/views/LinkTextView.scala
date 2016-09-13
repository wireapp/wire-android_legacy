/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.common.views

import android.content.Context
import android.os.SystemClock
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.{MotionEvent, ViewConfiguration}
import android.widget.TextView
import com.waz.utils.returning
import com.waz.zclient.common.views.LinkTextView.MovementMethod
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils

/**
  * TextView with improved handling for ClickableSpans.
  * It makes sure to only handle touch events on clickable span, not on the whole view.
  *
  * Warning: This view does not support text selection and regular click listeners.
  */
class LinkTextView(val context: Context, val attrs: AttributeSet, val defStyle: Int) extends TypefaceTextView(context, attrs, defStyle) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val movement = new MovementMethod(context)

  override def onTouchEvent(event: MotionEvent): Boolean =
    super.onTouchEvent(event) && movement.current.isDefined

  def setTextWithLink(text: String, color: Int, bold: Boolean = false, underline: Boolean = false)(onClick: => Unit) = {
    setText(text)
    TextViewUtils.boldText(this)
    TextViewUtils.linkifyText(this, color, bold, underline, new Runnable {
      override def run(): Unit = onClick
    })
    setMovementMethod(movement)
  }
}

object LinkTextView {

  class MovementMethod(context: Context) extends LinkMovementMethod {

    val touchSlop = ViewConfiguration.get(context).getScaledTouchSlop

    var current = Option.empty[ClickableSpan]
    var startPosition = (0f, 0f)
    var startTime = SystemClock.uptimeMillis()

    override def onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean = {

      def getLink = {
        var x: Int = event.getX.toInt
        var y: Int = event.getY.toInt
        x -= widget.getTotalPaddingLeft
        y -= widget.getTotalPaddingTop
        x += widget.getScrollX
        y += widget.getScrollY
        val layout = widget.getLayout
        val line = layout.getLineForVertical(y)
        val off = layout.getOffsetForHorizontal(line, x)
        buffer.getSpans(off, off, classOf[ClickableSpan]).headOption
      }

      event.getAction match {
        case MotionEvent.ACTION_DOWN =>
          current = getLink
          if (current.isDefined) {
            startPosition = (event.getX, event.getY)
            startTime = SystemClock.uptimeMillis()
          }
          current.isDefined
        case MotionEvent.ACTION_UP =>
          current foreach { _ onClick widget }
          returning(current.isDefined) { _ => current = None }
        case MotionEvent.ACTION_MOVE =>
          if (current.isDefined) {
            val dst = math.max(math.abs(event.getX - startPosition._1), math.abs(event.getY - startPosition._2))
            if (dst > touchSlop || SystemClock.uptimeMillis() - startTime > 750) {
              current = None
            }
          }
          current.isDefined
        case MotionEvent.ACTION_CANCEL =>
          current = None
          false
        case _ =>
          current.isDefined
      }
    }
  }
}
