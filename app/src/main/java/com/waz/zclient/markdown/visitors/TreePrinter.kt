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
package com.waz.zclient.markdown.visitors

import android.util.Log
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Node
import java.util.*

/**
 * An instance of TreePrinter visits each node (depth first) in the tree rooted at a given
 * node and simply prints summary information for each node into the console.
 */
class TreePrinter : AbstractVisitor() {

    companion object { val TAG = "!TreePrinter" }

    private var depth = 1
    private val stack = Stack<Int>()

    override fun visitChildren(parent: Node?) {
        if (parent == null) return

        // indentation for node depth
        val spaces = "\t".repeat(depth++)
        Log.d(TAG, spaces + parent)

        var child = parent.firstChild

        // for each child
        while (child != null) {
            val next = child.next

            // visit its children
            stack.push(depth)
            child.accept(this)
            depth = stack.pop()

            child = next
        }
    }
}
