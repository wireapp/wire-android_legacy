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
import android.graphics.{Canvas, Color, RectF}
import android.util.AttributeSet
import android.view.View
import com.waz.zclient.R
import com.waz.zclient.paintcode.StyleKitView.StyleKitDrawMethod

trait StyleKitView {

}

object StyleKitView {
  type StyleKitDrawMethod = (Canvas, RectF, WireStyleKit.ResizingBehavior, Int) => Unit
}


class RestoreIconView(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends View(context, attrs, defStyleAttr) with StyleKitView  {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val a = Option(attrs).map(a => getContext.obtainStyledAttributes(a, R.styleable.StyleKitView))
  private var color = a.map(_.getColor(R.styleable.StyleKitView_drawableColor, Color.WHITE)).getOrElse(Color.WHITE)

  setLayerType(View.LAYER_TYPE_SOFTWARE, null)

  override def onDraw(canvas: Canvas): Unit = {
    WireStyleKit.drawRestore(canvas, new RectF(getPaddingLeft, getPaddingTop, getWidth - getPaddingRight, getHeight - getPaddingBottom), WireStyleKit.ResizingBehavior.AspectFit, color)
  }

  def setColor(color: Int): Unit = {
    this.color = color
    invalidate()
  }
}

class GenericStyleKitView(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends View(context, attrs, defStyleAttr) with StyleKitView  {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val a = Option(attrs).map(a => getContext.obtainStyledAttributes(a, R.styleable.StyleKitView))
  private var color = a.map(_.getColor(R.styleable.StyleKitView_drawableColor, Color.WHITE)).getOrElse(Color.WHITE)

  def setSoftwareLayer(enabled: Boolean): Unit = if (enabled) setLayerType(View.LAYER_TYPE_SOFTWARE, null) else setLayerType(View.LAYER_TYPE_HARDWARE, null)

  setLayerType(View.LAYER_TYPE_SOFTWARE, null)

  var onDrawMethod: Option[StyleKitDrawMethod] = None

  def setOnDraw(fun:StyleKitDrawMethod): Unit = {
    onDrawMethod = Some(fun)
    invalidate()
  }

  override def onDraw(canvas: Canvas): Unit = {
    onDrawMethod.foreach(_(canvas, new RectF(getPaddingLeft, getPaddingTop, getWidth - getPaddingRight, getHeight - getPaddingBottom), WireStyleKit.ResizingBehavior.AspectFit, color))
  }

  def setColor(color: Int): Unit = {
    this.color = color
    invalidate()
  }
}
