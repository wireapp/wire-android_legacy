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
import android.text.style.URLSpan
import com.waz.zclient.markdown.spans.InlineSpan
import com.waz.zclient.markdown.spans.custom.MarkdownLinkSpan
import org.commonmark.node.Image
import org.commonmark.node.Node

/**
 * NOT YET SUPPORTED! Identical to LinkSpan
 * The span corresponding to the markdown "Image" unit.
 */
class ImageSpan(val url: String, val color: Int, onClick: (String) -> Unit) : InlineSpan() {

    init {
        add(MarkdownLinkSpan(url, onClick))
        add(ForegroundColorSpan(color))
    }

    override fun toNode(literal: String?): Node {
        val n = Image()
        n.destination = url
        return n
    }
}
