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
package com.waz.zclient.markdown.spans

import org.commonmark.node.Node

/**
 * A GroupSpan represents a grouping on Android span objects. It is used to combine individual
 * spans that together describe the styling of a single markdown unit. As such, all spans in
 * the group are treated as a single span. For example, a "HeadingSpan" could be composed of
 * spans responsible for individual styles, such as text size, text color and paragraph style.
 * Finally, since the span describes a markdown unit over a range of text, it can be used to
 * identify the various markdown units present within a given document.
 */
interface GroupSpan {

    // The priority indicates which spans should be considered first.
    // This is useful when reconstructing an AST from GroupSpan objects.
    enum class Priority {
        HIGH, MEDIUM, LOW
    }

    // The spans contained in the group.
    val spans: ArrayList<Any>

    // The priority of the group span.
    val priority: Priority get() = Priority.MEDIUM

    // Add a span to the group.
    fun add(span: Any)

    // The Commonmark node represented by the group.
    fun toNode(literal: String? = null) : Node
}
