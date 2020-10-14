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
    val stripeWidthSize: Int,
    val gapWidthSize: Int,
    private val density: Float = 1f,
    val beforeSpacing: Int = 0,
    val afterSpacing: Int = 0
) : QuoteSpan(color) {

    override fun getLeadingMargin(first: Boolean): Int {
        return ((stripeWidthSize + gapWidthSize) * density).toInt()
    }

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout
    ) {
        val spanned = text as Spanned
        val spanStart = spanned.getSpanStart(this)
        val spanEnd = spanned.getSpanEnd(this)

        // ensure this span is attached to the text
        if (!(spanStart <= start && spanEnd >= end)) return

        // we want to top and bottom of the stripe to align with the top and bottom of the text.
        // if there is before and after spacing applied, then we must counter it by offsetting
        // 'top' and 'bottom'.
        var topOffset = 0f
        var bottomOffset = 0f
        if (spanStart == start) { topOffset += beforeSpacing * density }
        if (spanEnd == end + 1) { bottomOffset -= afterSpacing * density }

        // save paint state
        val style = p.style
        val color = p.color

        p.style = Paint.Style.FILL
        p.color = this.color
        c.drawRect(x.toFloat(), top.toFloat() + topOffset, (x + dir * stripeWidthSize * density), bottom.toFloat() + bottomOffset, p)

        // reset paint
        p.style = style
        p.color = color
    }
}
