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
import android.text.Layout
import android.text.Spanned
import android.text.style.QuoteSpan

/**
 * CustomQuoteSpan extends QuoteSpan by allowing the width of the stripe and the gap between
 * the stripe and the content to be specified.
 */
class CustomQuoteSpan(
    color: Int,
    val stripeWidth: Int,
    val gapWidth: Int,
    private val density: Float = 1f
) : QuoteSpan(color) {

    override fun getLeadingMargin(first: Boolean): Int {
        return ((stripeWidth + gapWidth) * density).toInt()
    }

    override fun drawLeadingMargin(
        c: Canvas?, p: Paint?, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence?, start: Int, end: Int, first: Boolean, layout: Layout?
    ) {
        // ensure this span is attached to the text
        val spanned = text as Spanned
        if (!(spanned.getSpanStart(this) == start && spanned.getSpanEnd(this) == end)) return

        if (c == null || p == null) return

        // save paint state
        val style = p.style
        val color = p.color

        p.style = Paint.Style.FILL
        p.color = this.color
        c.drawRect(x.toFloat(), top.toFloat(), (x + dir * stripeWidth * density).toFloat(), bottom.toFloat(), p)

        // reset paint
        p.style = style
        p.color = color
    }
}
