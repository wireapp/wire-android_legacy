package com.waz.zclient.utils

import android.content.Context
import android.graphics.{Bitmap, Canvas, Paint, PorterDuff, PorterDuffColorFilter, RectF}
import com.waz.utils.returning
import com.waz.zclient.paintcode.WireStyleKit
import com.waz.zclient.R

final case class StyleKitMethods(implicit context: Context) {

  def drawFileBlocked(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    StyleKitMethods.drawBitmap(canvas, targetFrame, color, R.attr.fileBlocked)

}

object StyleKitMethods {

  def drawBitmap(canvas: Canvas, targetFrame: RectF, color: Int, resourceId: Int)(implicit context: Context): Unit =
    ContextUtils.getStyledDrawable(resourceId, context.getTheme).foreach { drawable =>
      val paint = returning(new Paint) { p =>
        p.reset()
        p.setFlags(Paint.ANTI_ALIAS_FLAG)
        p.setStyle(Paint.Style.FILL)
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP))
      }
      val width = targetFrame.width().toInt
      val height = targetFrame.height().toInt
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val c = new Canvas(bitmap)
      drawable.setBounds(0, 0, c.getWidth, c.getHeight)
      drawable.draw(c)
      canvas.drawBitmap(bitmap, targetFrame.left, targetFrame.top, paint)
    }

}

