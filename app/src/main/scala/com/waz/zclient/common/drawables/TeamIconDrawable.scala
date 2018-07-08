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
package com.waz.zclient.drawables

import android.content.Context
import android.graphics._
import android.graphics.drawable.Drawable
import com.waz.ZLog
import com.waz.model.{AssetData, AssetId}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache.BitmapRequest.Single
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.zclient.drawables.TeamIconDrawable._
import com.waz.zclient.{Injectable, Injector, R}

object TeamIconDrawable {
  val TeamCorners = 6
  val UserCorners = 0
}

class TeamIconDrawable(implicit inj: Injector, eventContext: EventContext, ctx: Context) extends Drawable with Injectable {
  private implicit val tag = ZLog.logTagFor[TeamIconDrawable]

  var text = ""
  var corners = UserCorners
  var selected = false

  private val selectedDiameter = ctx.getResources.getDimensionPixelSize(R.dimen.team_tab_drawable_diameter)
  private val borderGapWidth = ctx.getResources.getDimensionPixelSize(R.dimen.team_tab_drawable_gap)
  private val borderWidth = ctx.getResources.getDimensionPixelSize(R.dimen.team_tab_drawable_border)
  private lazy val unselectedDiameter = selectedDiameter + 2 * (borderGapWidth + borderWidth)

  val borderPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { paint =>
    paint.setColor(Color.TRANSPARENT)
    paint.setStyle(Paint.Style.STROKE)
    paint.setStrokeJoin(Paint.Join.ROUND)
    paint.setStrokeCap(Paint.Cap.ROUND)
    paint.setDither(true)
    paint.setPathEffect(new CornerPathEffect(10f))
  }

  val innerPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { paint =>
    paint.setColor(Color.TRANSPARENT)
    paint.setStyle(Paint.Style.FILL)
    paint.setStrokeJoin(Paint.Join.ROUND)
    paint.setStrokeCap(Paint.Cap.ROUND)
    paint.setDither(true)
    paint.setPathEffect(new CornerPathEffect(6f))
  }

  val textPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)){ paint =>
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR))
    paint.setTextAlign(Paint.Align.CENTER)
    paint.setColor(Color.TRANSPARENT)
    paint.setAntiAlias(true)
  }

  val bitmapPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)){ paint =>
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
  }

  val innerPath = new Path()
  val borderPath = new Path()
  val matrix = new Matrix()

  val assetId = Signal(Option.empty[AssetId])
  val bounds = Signal[Rect]()
  val zms = inject[Signal[ZMessaging]]

  val bmp = for{
    z <- zms
    asset <- assetId.flatMap {
      case Some(aId) => z.assetsStorage.signal(aId).map(Option(_))
      case _ => Signal.const(Option.empty[AssetData])
    }
    b <- bounds
    bmp <- asset.fold {
      Signal.const(Option.empty[Bitmap])
    } { assetData =>
      BitmapSignal(z, assetData, Single(b.width)).collect { case BitmapLoaded(bm, _) => Option(bm) }
    }
  } yield bmp

  private var currentBmp = Option.empty[Bitmap]

  bmp.onUi{ bitmap =>
    currentBmp = bitmap
    invalidateSelf()
  }

  bounds.on(Threading.Ui) { bounds =>
    updateDrawable(bounds)
  }

  override def draw(canvas: Canvas) = {
    canvas.drawPath(innerPath, innerPaint)
    currentBmp match {
      case Some(bitmap) =>
        matrix.reset()
        computeMatrix(bitmap.getWidth, bitmap.getHeight, getBounds, matrix)
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
      case _ =>
        val textY = getBounds.centerY - ((textPaint.descent + textPaint.ascent) / 2f)
        val textX = getBounds.centerX
        canvas.drawText(text, textX, textY, textPaint)
    }
    if (selected) canvas.drawPath(borderPath, borderPaint)
  }

  def computeMatrix(bmWidth: Int, bmHeight: Int, bounds: Rect, matrix: Matrix): Unit = {

    val selectedOffset = if (selected)
      (selectedDiameter + 2 * (borderGapWidth + borderWidth)).toFloat - selectedDiameter.toFloat
    else
      0.0f

    val scale = math.max((bounds.width.toFloat - selectedOffset) / bmWidth.toFloat, (bounds.height.toFloat - selectedOffset) / bmHeight.toFloat)
    val dx = - (bmWidth * scale - bounds.width) / 2
    val dy = - (bmHeight * scale - bounds.height) / 2

    matrix.setScale(scale, scale)
    matrix.postTranslate(dx, dy)
  }

  override def setColorFilter(colorFilter: ColorFilter) = {
    borderPaint.setColorFilter(colorFilter)
    innerPaint.setColorFilter(colorFilter)
  }

  override def setAlpha(alpha: Int) = {
    borderPaint.setAlpha(alpha)
    innerPaint.setAlpha(alpha)
  }

  override def getOpacity = {
    borderPaint.getAlpha
    borderPaint.getAlpha
  }

  private def drawPolygon(path: Path, radius: Float, corners: Int): Unit = {
    path.reset()
    if (corners == 0) {
      path.addCircle(0, 0, radius, Path.Direction.CW)
    } else {
      val angle = 2 * Math.PI / corners
      val phase = angle / 2
      (0 until corners).foreach{ i =>
        val x = radius * Math.cos(angle * i + phase)
        val y = radius * Math.sin(angle * i + phase)
        if (i == 0) {
          path.moveTo(x.toFloat, y.toFloat)
        } else {
          path.lineTo(x.toFloat, y.toFloat)
        }
      }
      path.close()
    }
  }

  override def onBoundsChange(bounds: Rect) = {
    this.bounds ! bounds
  }

  override def getIntrinsicHeight = super.getIntrinsicHeight

  override def getIntrinsicWidth = super.getIntrinsicWidth

  private def updateDrawable(bounds: Rect): Unit = {
    val scale = Math.min(bounds.width(), bounds.height()).toFloat / (unselectedDiameter.toFloat + borderGapWidth + borderWidth)

    val diam = scale * (if (selected) selectedDiameter else unselectedDiameter)
    val textSize = diam / 2.5f

    drawPolygon(innerPath, diam / 2, corners)
    drawPolygon(borderPath, diam / 2 + borderGapWidth + borderWidth, corners)

    textPaint.setTextSize(textSize)
    borderPaint.setStrokeWidth(borderWidth)

    val hexMatrix = new Matrix()
    hexMatrix.setTranslate(bounds.centerX(), bounds.centerY())
    borderPath.transform(hexMatrix)
    innerPath.transform(hexMatrix)
    invalidateSelf()
  }

  def setInfo(text: String, corners: Int, selected: Boolean): Unit = {
    this.text = text
    innerPaint.setColor(Color.WHITE)
    this.corners = corners
    this.selected = selected
    bounds.currentValue.foreach(updateDrawable)
  }

  def setBorderColor(color: Int): Unit = {
    borderPaint.setColor(color)
    invalidateSelf()
  }
}
