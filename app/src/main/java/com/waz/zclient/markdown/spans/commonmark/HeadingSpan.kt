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

import android.graphics.Typeface
import android.text.style.AbsoluteSizeSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.waz.zclient.markdown.spans.BlockSpan
import com.waz.zclient.markdown.spans.custom.ParagraphSpacingSpan
import org.commonmark.node.Heading
import org.commonmark.node.Node

/**
 * The span corresponding to the markdown "Heading" unit. Note, the font size is densitiy
 * independent.
 */
class HeadingSpan(
    val level: Int,
    val fontSizeMultiplier: Float,
    val beforeSpacing: Int,
    val afterSpacing: Int
) : BlockSpan() {

    init {
        add(RelativeSizeSpan(fontSizeMultiplier))
        add(ParagraphSpacingSpan(beforeSpacing, afterSpacing))
        add(StyleSpan(Typeface.BOLD))
    }

    override fun toNode(literal: String?): Node {
        val n = Heading()
        n.level = level
        return n
    }
}
