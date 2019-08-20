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
package com.waz.zclient.paintcode

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics._
import com.waz.model.EphemeralDuration.TimeUnit
import com.waz.utils.returning
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.paintcode.WireStyleKit._
import com.waz.zclient.utils.ResColor

trait WireDrawable extends Drawable {

  protected val paint = new Paint()
  protected val padding = new Rect(0, 0, 0, 0)

  override def setColorFilter(colorFilter: ColorFilter): Unit = paint.setColorFilter(colorFilter)

  override def getOpacity: Int = paint.getAlpha

  override def setAlpha(alpha: Int): Unit = paint.setAlpha(alpha)

  def setColor(color: Int): Unit = {
    paint.setColor(color)
    invalidateSelf()
  }

  protected def getDrawingRect = new RectF(getBounds.left + padding.left, getBounds.top + padding.top, getBounds.right - padding.right, getBounds.bottom - padding.bottom)

  def setPadding(rect: Rect): Unit = {
    padding.set(rect)
    invalidateSelf()
  }

  override def getPadding(padding: Rect): Boolean = {
    padding.set(this.padding)
    true
  }
}

case class DownArrowDrawable() extends WireDrawable {
  override def draw(canvas: Canvas): Unit =
    drawDownArrow(canvas, new RectF(canvas.getClipBounds), ResizingBehavior.AspectFit, paint.getColor)
}

case class ServicePlaceholderDrawable(cornerRadius: Float = 0, backgroundColor: Int = Color.WHITE) extends WireDrawable {
  import ServicePlaceholderDrawable._

  private val StrokeWidth = 2f
  private val bgPaint = returning(new Paint())(_.setColor(backgroundColor))
  private val strokePaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)){ paint =>
    paint.setStyle(Paint.Style.STROKE)
    paint.setColor(Color.BLACK)
    paint.setAlpha(20)
    paint.setStrokeWidth(StrokeWidth)
  }
  paint.setAlpha(20)

  override def draw(canvas: Canvas): Unit = {
    val b = canvas.getClipBounds

    val w: Float = b.right - b.left
    val h: Float = b.bottom - b.top

    val wi = w * InnerSizeFactor
    val hi = h * InnerSizeFactor

    val li = w * (1f - InnerSizeFactor) / 2f
    val ti = h * (1f - InnerSizeFactor) / 2f
    val rectInner = new RectF(li, ti, li + wi, ti + hi)

    val strokeRect = new RectF(StrokeWidth, StrokeWidth, getBounds.width - StrokeWidth, getBounds.height - StrokeWidth)
    val bgRect = new RectF(StrokeWidth * 2, StrokeWidth * 2, getBounds.width - StrokeWidth * 2, getBounds.height - StrokeWidth * 2)

    canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
    canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, strokePaint)
    drawServiceIcon(canvas, rectInner, ResizingBehavior.AspectFit, paint.getColor)
  }

  override def setAlpha(alpha: Int): Unit = {
    strokePaint.setAlpha((0.08 * alpha).toInt)
    paint.setAlpha((0.08 * alpha).toInt)
    bgPaint.setAlpha(alpha)
  }
}

object ServicePlaceholderDrawable {
  val InnerSizeFactor = 0.5f
}

case class CreateGroupIcon(colorRes: Int)(implicit context: Context) extends WireDrawable {
  setColor(getColor(colorRes))
  override def draw(canvas: Canvas) = drawGroupIcon(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
}

case class ManageServicesIcon(color: ResColor)(implicit context: Context) extends WireDrawable {
  setColor(color.resolve)
  override def draw(canvas: Canvas) = drawServiceIcon(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
}

case class GuestIcon(colorRes: Int)(implicit context: Context) extends WireDrawable {
  setColor(getColor(colorRes))
  override def draw(canvas: Canvas) = drawGuestIcon(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
}

case class GuestIconWithColor(color: Int)(implicit context: Context) extends WireDrawable {
  setColor(color)
  override def draw(canvas: Canvas) = drawGuestIcon(canvas, new RectF(canvas.getClipBounds), ResizingBehavior.AspectFit, paint.getColor)
}

case class ViewWithColor(color: Int)(implicit context: Context) extends WireDrawable {
  setColor(color)
  override def draw(canvas: Canvas) = drawView(canvas, new RectF(canvas.getClipBounds), ResizingBehavior.AspectFit, paint.getColor)
}

case class ForwardNavigationIcon(colorRes: Int)(implicit context: Context) extends WireDrawable {
  setColor(getColor(colorRes))
  override def draw(canvas: Canvas) = drawNavigationArrow(canvas, new RectF(canvas.getClipBounds), ResizingBehavior.AspectFit, paint.getColor)
}

case class DropdownIcon(colorRes: Int)(implicit context: Context) extends WireDrawable {
  setColor(getColor(colorRes))
  override def draw(canvas: Canvas) = drawDropdown(canvas, new RectF(canvas.getClipBounds), ResizingBehavior.AspectFit, paint.getColor)
}

case class BackupRestoreIcon(color: Int)(implicit context: Context) extends WireDrawable {
  setColor(color)
  override def draw(canvas: Canvas) = drawRestore(canvas, new RectF(canvas.getClipBounds), ResizingBehavior.AspectFit, paint.getColor)
}

case class VideoIcon(colorRes: Int)(implicit context: Context) extends WireDrawable {
  setColor(getColor(colorRes))
  override def draw(canvas: Canvas) = drawCamera(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
}

case class ConversationIcon(colorRes: Int)(implicit context: Context) extends WireDrawable {
  setColor(getColor(colorRes))
  override def draw(canvas: Canvas) = drawConversation(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
}

case class NotificationsIcon(color: Int)(implicit context: Context) extends WireDrawable {
  setColor(color)
  override def draw(canvas: Canvas) = drawAlerts(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
}

case class EphemeralIcon(color: Int, timeUnit: TimeUnit)(implicit context: Context) extends WireDrawable {
  setColor(color)
  import com.waz.model.EphemeralDuration._
  override def draw(canvas: Canvas) = {
    timeUnit match {
      case Second => drawSecond(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
      case Minute => drawMinute(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
      case Hour   => drawHour(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
      case Day    => drawDay(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
      case Week   => drawWeek(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
      case Year   => drawYear(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
    }
  }
}

case class HourGlassIcon(color: Int)(implicit context: Context) extends WireDrawable {
  setColor(color)
  override def draw(canvas: Canvas) = drawTimedMessages(canvas, getDrawingRect, ResizingBehavior.AspectFit, paint.getColor)
}
