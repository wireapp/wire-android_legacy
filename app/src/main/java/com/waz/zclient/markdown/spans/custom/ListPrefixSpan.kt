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
package com.waz.zclient.markdown.spans.custom

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spanned
import android.text.style.ParagraphStyle
import android.text.style.ReplacementSpan

/**
 * A ListPrefixSpan simply monospaces the spanned text and right aligns to a specified container
 * width. The `digits` property is the max number of digits containers and `digitWidth` is the
 * width of each container.
 */
class ListPrefixSpan(
    val digits: Int,
    val digitWidth: Int,
    val color: Int
) : ReplacementSpan(), ParagraphStyle {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm == null) return (end - start) * digitWidth
        return paint.getFontMetricsInt(fm)
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        // ensure this span is attached to the text
        val spanned = text as Spanned
        if (!(spanned.getSpanStart(this) == start && spanned.getSpanEnd(this) == end)) return

        // the list prefix
        val prefix = text.subSequence(start until end)

        // total container width
        val totalWidth = (digits * digitWidth).toFloat()

        // actual width of the current prefix
        val prefixWidth = getSize(paint, text, start, end, null).toFloat()

        // the free space
        val baseOffset = totalWidth - prefixWidth

        val oldColor = paint.color
        paint.color = color

        for (i in prefix.indices) {
            val charWidth = paint.measureText(prefix, i, i + 1)
            val halfFreeSpace = (digitWidth - charWidth) / 2f
            val charOffset = (i * digitWidth) + halfFreeSpace
            canvas.drawText(prefix, i, i + 1, x + baseOffset + charOffset, y.toFloat(), paint)
        }

        paint.color = oldColor
    }
}
