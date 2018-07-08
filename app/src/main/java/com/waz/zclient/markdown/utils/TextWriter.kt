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

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import com.waz.zclient.markdown.spans.GroupSpan
import java.util.*

/**
 * A TextWriter object facilitates the construction of a SpannableString by providing simple
 * declarative methods to append content and set GroupSpans.
 */
class TextWriter {

    private val builder = SpannableStringBuilder()
    private val cursorStack=  Stack<Int>()

    /**
     * The current cursor index in the text. This increments whenever content is appended.
     */
    var cursor = 0

    /**
     * The SpannableString derived from the current text.
     */
    val spannableString: SpannableString get() = SpannableString(builder)

    /**
     * The plain text string of the current buffer.
     */
    override fun toString(): String = builder.toString()

    /**
     * Sets the given span with the flag SPAN_EXCLUSIVE_EXCLUSIVE over the given range (inclusive).
     */
    fun set(span: Any, start: Int, end: Int) {
        builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * Sets the given group span (as well as its containing spans) with the flag
     * SPAN_EXCLUSIVE_EXCLUSIVE over the given range (inclusive). If no end index is provided,
     * the span will extend to the end of the document.
     */
    fun set(groupSpan: GroupSpan, start: Int, end: Int = cursor) {
        set(groupSpan as Any, start, end)
        groupSpan.spans.forEach { set(it, start, end) }
    }

    /**
     * Writes the content string to the end of the document and updates the cursor position.
     */
    fun write(content: String) {
        builder.append(content)
        cursor += content.length
    }

    /**
     * Appends the given char if it is currently not the last char. Returns true if successful.
     */
    fun appendIfNeeded(char: Char): Boolean {
        when (builder.lastOrNull()) {
            char -> return false
            else -> { write(char.toString()); return true }
        }
    }

    /**
     * Appends a line break and updates the cursor position.
     */
    fun line() = write("\n")

    /**
     * Returns `true` if a linebreak was successfully appended.
     */
    fun lineIfNeeded(): Boolean = appendIfNeeded('\n')

    /**
     * Appends a space and updates the cursor position.
     */
    fun space() = write(" ")

    /**
     * Returns `true` if a space was successfully appended.
     */
    fun spaceIfNeeded(): Boolean = appendIfNeeded(' ')

    /**
     * Appends a tab and update the cursor position.
     */
    fun tab() = write("\t")

    /**
     * Returns `true` if a tab was successfully appended.
     */
    fun tabIfNeeded(): Boolean = appendIfNeeded('\t')

    /**
     * Returns the current cursor position after pushing it to the stack.
     */
    fun saveCursor(): Int = cursorStack.push(cursor)

    /**
     * Pops and returns the saved cursor from the top of the stack. Throws an
     * `EmptyStackException` if there is no saved cursor.
     */
    fun retrieveCursor(): Int = cursorStack.pop()

}
