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
package com.waz.zclient.markdown.utils

import org.commonmark.internal.renderer.text.ListHolder
import org.commonmark.internal.renderer.text.OrderedListHolder
import org.commonmark.node.*

/**
 * Helper extention properties and methods.
 */

/**
 * The paragraph node is the root or the direct child of a Document node.
 */
val Paragraph.isOuterMost: Boolean get() = parent is Document?

/**
 * The block quote's direct parent is also a BlockQuote node.
 */
val BlockQuote.isNested: Boolean get() = parent is BlockQuote

/**
 * The block quote node is the root or the direct child of a Document node.
 */
val BlockQuote.isOuterMost: Boolean get() = parent is Document?

/**
 * The number of parents of this list holder.
 */
val ListHolder.depth: Int get() {
    var depth = 0
    var next = parent
    while (next != null) { depth++; next = next.parent }
    return depth
}

/**
 * True if the list holder has no parents.
 */
val ListHolder.isRoot: Boolean get() = depth == 0

/**
 * The number of direct children of this node.
 */
val Node.numberOfChildren: Int get() {
    var count = 0
    var child: Node? = firstChild
    while (child != null) { count++; child = child.next }
    return count
}

/**
 * Returns true if the list item contains a line break. If `includeSoft` is true,
 * soft line breaks will be considered.
 */
fun ListItem.hasLineBreak(includeSoft: Boolean = false): Boolean {
    // the sole child of a list item is a paragraph
    var node = firstChild
    if (node !is Paragraph) return false

    // iterate through children of paragraph
    node = node.firstChild
    while (node != null) {
        // return true on first occurence of line break
        if (node is HardLineBreak || (includeSoft && node is SoftLineBreak)) return true
        node = node.next
    }

    return false
}

/**
 * The number of items in this list. Note, this assumes that each item is a direct child
 * of this node.
 */
val OrderedList.numberOfItems: Int get() = numberOfChildren

/**
 * The prefix number of the last item in the list.
 */
val OrderedList.largestPrefix: Int get() = startNumber + numberOfItems - 1

/**
 * The number of digits in this number.
 */
val Int.numberOfDigits: Int get() {
    var e = 1
    while (this.toDouble() >= Math.pow(10.0, e.toDouble())) e++
    return e
}
