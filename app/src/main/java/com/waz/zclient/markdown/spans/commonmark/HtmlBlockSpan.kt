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
package com.waz.zclient.markdown.spans.commonmark

import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.TypefaceSpan
import com.waz.zclient.markdown.spans.BlockSpan
import com.waz.zclient.markdown.spans.custom.ParagraphSpacingSpan
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Node

/**
 * The span corresponding to the markdown "HtmlBlock" unit.
 */
class HtmlBlockSpan(
    val color: Int,
    val indentation: Int,
    val beforeSpacing: Int,
    val afterSpacing: Int
) : BlockSpan() {

    init {
        add(TypefaceSpan("monospace"))
        add(LeadingMarginSpan.Standard(indentation))
        add(ForegroundColorSpan(color))
        add(ParagraphSpacingSpan(beforeSpacing, afterSpacing))
    }

    override fun toNode(literal: String?): Node {
        val n = HtmlBlock()
        n.literal = literal ?: ""
        return n
    }
}
