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
package com.waz.zclient.common.drawables

import android.content.Context
import android.graphics._
import android.graphics.drawable.Drawable
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.Picture
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.drawables.TeamIconDrawable._
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.Future

object TeamIconDrawable {
  
  sealed trait Shape
  object UserShape extends Shape
  object TeamShape extends Shape
}

class TeamIconDrawable(implicit inj: Injector, eventContext: EventContext, ctx: Context)
  extends Drawable
    with Injectable
    with DerivedLogTag {

  private val imageAsset = Signal(Option.empty[Picture])
  private var text = ""
  private var iconShape: Shape = UserShape
  private var isSelected = false

  private val borderGapWidth = ctx.getResources.getDimensionPixelSize(R.dimen.team_tab_drawable_gap)
  private val borderWidth = ctx.getResources.getDimensionPixelSize(R.dimen.team_tab_drawable_border)

  private val borderPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { paint =>
    paint.setColor(Color.TRANSPARENT)
    paint.setStyle(Paint.Style.STROKE)
    paint.setStrokeJoin(Paint.Join.ROUND)
    paint.setStrokeCap(Paint.Cap.ROUND)
    paint.setDither(true)
  }

  private val innerPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { paint =>
    paint.setColor(Color.TRANSPARENT)
    paint.setStyle(Paint.Style.FILL)
    paint.setStrokeJoin(Paint.Join.ROUND)
    paint.setStrokeCap(Paint.Cap.ROUND)
    paint.setDither(true)
  }

  private val textPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)){ paint =>
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR))
    paint.setTextAlign(Paint.Align.CENTER)
    paint.setColor(Color.TRANSPARENT)
    paint.setAntiAlias(true)
  }

  private val bitmapPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)){ paint =>
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
  }

  private val bounds = Signal[Rect]()
  private val innerPath = new Path()
  private val borderPath = new Path()
  private val matrix = new Matrix()

  private var currentBitmap = Option.empty[Bitmap]

  private val bitmap = for {
    bounds <- bounds
    asset <- imageAsset
    bitmap <- Signal.future(
      asset.fold(Future.successful(Option.empty[Bitmap])) { p =>
        Threading.ImageDispatcher {
          Option(WireGlide(ctx)
            .asBitmap()
            .load(p)
            .submit(bounds.width, bounds.height)
            .get())
        }.future
      })
  } yield bitmap

  bitmap .onUi{ bitmap =>
    currentBitmap = bitmap
    invalidateSelf()
  }

  bounds.on(Threading.Ui) { bounds =>
    updateDrawable(bounds)
  }

  def setPicture(picture: Option[Picture]): Unit = imageAsset ! picture

  def setInfo(text: String, shape: Shape, selected: Boolean): Unit = {
    this.text = text
    innerPaint.setColor(Color.WHITE)
    iconShape = shape
    this.isSelected = selected
    bounds.currentValue.foreach(updateDrawable)
  }

  def setBorderColor(color: Int): Unit = {
    borderPaint.setColor(color)
    invalidateSelf()
  }

  override def draw(canvas: Canvas): Unit = {
    canvas.drawPath(innerPath, innerPaint)
    currentBitmap match {
      case Some(bitmap) =>
        matrix.reset()
        computeMatrix(bitmap.getWidth, bitmap.getHeight, getBounds, matrix)
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
      case _ =>
        val textY = getBounds.centerY - ((textPaint.descent + textPaint.ascent) / 2f)
        val textX = getBounds.centerX
        canvas.drawText(text, textX, textY, textPaint)
    }
    if (isSelected) canvas.drawPath(borderPath, borderPaint)
  }

  def computeMatrix(bmWidth: Int, bmHeight: Int, bounds: Rect, matrix: Matrix): Unit = {
    val borderAndGaps = (2 * (borderGapWidth + borderWidth)).toFloat
    val availableWidth = bounds.width.toFloat - borderAndGaps
    val availableHeight = bounds.height.toFloat - borderAndGaps

    val widthRatio = availableWidth / bmWidth.toFloat
    val heightRatio = availableHeight / bmHeight.toFloat
    val scale = math.max(widthRatio, heightRatio)

    val dx = - (bmWidth * scale - bounds.width) / 2
    val dy = - (bmHeight * scale - bounds.height) / 2

    matrix.setScale(scale, scale)
    matrix.postTranslate(dx, dy)
  }

  override def setColorFilter(colorFilter: ColorFilter): Unit = {
    borderPaint.setColorFilter(colorFilter)
    innerPaint.setColorFilter(colorFilter)
  }

  override def setAlpha(alpha: Int): Unit = {
    borderPaint.setAlpha(alpha)
    innerPaint.setAlpha(alpha)
  }

  override def getOpacity: Int = {
    borderPaint.getAlpha
    borderPaint.getAlpha
  }

  override def onBoundsChange(bounds: Rect): Unit = {
    this.bounds ! bounds
  }

  override def getIntrinsicHeight: Int = super.getIntrinsicHeight

  override def getIntrinsicWidth: Int = super.getIntrinsicWidth

  private def updateDrawable(bounds: Rect): Unit = {
    val smallestSide = math.min(bounds.width(), bounds.height()).toFloat
    val borderDiameter = smallestSide - 2 * borderWidth
    val imageDiameter = borderDiameter - 2 * borderGapWidth

    drawPolygon(innerPath, imageDiameter / 2, iconShape)
    drawPolygon(borderPath, borderDiameter / 2, iconShape)

    val textSize = imageDiameter / 2.5f
    textPaint.setTextSize(textSize)
    borderPaint.setStrokeWidth(borderWidth)

    val matrix = new Matrix()
    matrix.setTranslate(bounds.centerX(), bounds.centerY())
    borderPath.transform(matrix)
    innerPath.transform(matrix)
    invalidateSelf()
  }

  private def drawPolygon(path: Path, radius: Float, shape: Shape): Unit = {
    path.reset()
    shape match {
      case UserShape => path.addCircle(0, 0, radius, Path.Direction.CW)
      case TeamShape => path.addRoundRect(-radius, -radius, radius, radius, radius / 4, radius / 4, Path.Direction.CW)
    }
  }
}
