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

import android.graphics.Paint
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LineHeightSpan

/**
 * ParagraphSpacingSpan allows the spacing before and after a paragraph to be specified.
 * Note: like all paragraph spans, it must be spanned over the first and last characters
 * in the paragraph.
 *
 * Initial idea from: https://stackoverflow.com/questions/25776082/paragraph-spacings-using-spannablestringbuilder-in-textview
 * by Durgadass S
 */
class ParagraphSpacingSpan(val before: Int, val after: Int) : LineHeightSpan.WithDensity {

    private var firstTime = true
    private var ascent: Int? = null
    private var top: Int? = null

    override fun chooseHeight(text: CharSequence?, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt?) {
        chooseHeight(text, start, end, spanstartv, v, fm, null)
    }

    override fun chooseHeight(
        text: CharSequence?, start: Int, end: Int,
        spanstartv: Int, v: Int, fm: Paint.FontMetricsInt?, paint: TextPaint?
    ) {
        if (text !is Spanned) return

        val density = paint?.density ?: 1f

        // store the initial values
        if (firstTime && fm != null) {
            firstTime = false
            ascent = fm.ascent
            top = fm.top
        }

        val spanStart = text.getSpanStart(this)
        val spanEnd = text.getSpanEnd(this)

        // restore the font metrics. It seems that once we change the metrics, they propagate
        // to each call, affecting the middle lines.
        if (!firstTime) {
            fm?.let {
                fm.ascent = ascent!!
                fm.top = top!!
            }
        }

        // if first line
        if (spanStart == start) {
            fm?.let {
                fm.ascent -= (density * before).toInt()
                fm.top -= (density * before).toInt()
            }
        }

        // if last line
        if (spanEnd == end) {
            fm?.let {
                fm.descent += (density * after).toInt()
                fm.bottom += (density * after).toInt()
            }
        }
    }
}
