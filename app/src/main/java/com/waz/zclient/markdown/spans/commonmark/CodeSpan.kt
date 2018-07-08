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
import android.text.style.TypefaceSpan
import com.waz.zclient.markdown.spans.GroupSpan
import com.waz.zclient.markdown.spans.InlineSpan
import org.commonmark.node.Code
import org.commonmark.node.Node

/**
 * The span corresponding to the markdown "Code" unit.
 */
class CodeSpan(val color: Int) : InlineSpan() {

    init {
        add(TypefaceSpan("monospace"))
        add(ForegroundColorSpan(color))
    }

    override val priority: GroupSpan.Priority get() = GroupSpan.Priority.LOW

    override fun toNode(literal: String?): Node = Code(literal ?: "")
}

