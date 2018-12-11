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
package com.waz.zclient.messages

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, Paint}
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.returning
import com.waz.zclient.common.views.LinkTextView
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.RichView

/**
  * View implementing system message layout: row containing icon, text and expandable line.
  * By hard-coding layout logic in this class we can avoid using complicated view hierarchies.
  */
class SystemMessageView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.system_message_content)

  val start = getDimenPx(R.dimen.content__padding_left)

  val textMargin = getDimenPx(R.dimen.wire__padding__12)
  val paddingTop = getDimenPx(R.dimen.wire__padding__small)
  val stroke     = getDimenPx(R.dimen.wire__divider__height)

  val dividerColor = {
    val a = context.obtainStyledAttributes(Array(R.attr.wireDividerColor))
    returning(a.getColor(0, getColor(R.color.separator_dark))) { _ => a.recycle() }
  }

  val paint = returning(new Paint()) { p =>
    p.setColor(dividerColor)
    p.setStrokeWidth(stroke)
  }

  val iconView: GlyphTextView = findById(R.id.gtv__system_message__icon)
  val textView: LinkTextView = findById(R.id.ttv__system_message__text)


  private var hasDivider = true
  setHasDivider(true)

  def setHasDivider(hasDivider: Boolean) = {
    this.hasDivider = hasDivider
    textView.setMarginRight(getDimenPx(if (hasDivider) R.dimen.content__padding_left else R.dimen.wire__padding__24))
    setWillNotDraw(!hasDivider)
  }

  def setText(text: String) = {
    textView.setText(text)
    TextViewUtils.boldText(textView)
  }

  def setTextWithLink(text: String, color: Int, bold: Boolean = false, underline: Boolean = false)(onClick: => Unit) =
    textView.setTextWithLink(text, color, bold, underline)(onClick)

  def setIcon(drawable: Drawable) = {
    iconView.setText("")
    iconView.setBackground(drawable)
  }

  def setIcon(resId: Int) = {
    iconView.setText("")
    iconView.setBackgroundResource(resId)
  }

  def setIconGlyph(glyph: String) = {
    iconView.setBackground(null)
    iconView.setText(glyph)
    iconView.setTextColor(getColor(R.color.light_graphite))
  }

  def setIconGlyph(resId: Int) = {
    iconView.setBackground(null)
    iconView.setText(resId)
    iconView.setTextColor(getColor(R.color.light_graphite))
  }

  override def onDraw(canvas: Canvas): Unit = {
    super.onDraw(canvas)

    if (hasDivider && textView.getText.length() > 0) {
      val y = paddingTop + stroke / 2f
      val w = getWidth - start - textView.getWidth - textMargin
      val l = if (getLayoutDirection == View.LAYOUT_DIRECTION_RTL) 0 else getWidth - w
      canvas.drawLine(l, y, l + w, y, paint)
    }
  }
}
