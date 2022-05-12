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
package com.waz.zclient.markdown

import android.graphics.Color
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waz.zclient.markdown.spans.GroupSpan
import com.waz.zclient.markdown.spans.InlineSpan
import com.waz.zclient.markdown.utils.TextWriter
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextWriterTest {

    @Test
    fun testThatItAppendsACharWhenBufferIsEmpty() {
        // given
        val sut = TextWriter()
        assertTrue(sut.toString().isEmpty())

        // when
        val didAppend = sut.appendIfNeeded('a')

        // then
        assertTrue(didAppend)
        assertEquals("a", sut.toString())
    }

    @Test
    fun testThatItAppendsACharWhenLastCharIsDifferent() {
        // given
        val sut = TextWriter()
        assertTrue(sut.toString().isEmpty())

        // when
        val didAppend = sut.appendIfNeeded('a')

        // then
        assertTrue(didAppend)
        assertEquals("a", sut.toString())
    }

    @Test
    fun testThatItDoesNotAppendACharWhenItIsAlreadyAppended() {
        // given
        val sut = TextWriter()
        sut.write("hello!")

        // when
        val didAppend = sut.appendIfNeeded('!')

        // then
        assertFalse(didAppend)
        assertEquals("hello!", sut.toString())
    }

    @Test
    fun testThatItUpdatesTheCursorWhenContentIsWritten() {
        // given
        val sut = TextWriter()
        assertTrue(sut.toString().isEmpty())
        assertEquals(0, sut.cursor)

        // when
        sut.write("hello!")

        // then
        assertEquals("hello!", sut.toString())
        assertEquals(6, sut.cursor)
    }

    @Test
    fun testThatItStoresAndRetrievesTheCursor() {
        // given
        val sut = TextWriter()
        sut.write("hello, ")
        assertEquals(7, sut.cursor)

        // when
        sut.saveCursor()
        sut.write("world!")
        sut.saveCursor()
        sut.write(" goodbye!")

        // then
        assertEquals(13, sut.retrieveCursor())
        assertEquals(7, sut.retrieveCursor())
        assertEquals(22, sut.cursor)
    }

    @Test
    fun testThatItSetsASpan() {
        // given
        val sut = TextWriter()
        sut.write("hello, world!")

        // when
        sut.set(ForegroundColorSpan(Color.BLUE), 7, 12)

        // then
        val string = sut.spannableString
        val spans = string.getSpans(0, 13, ForegroundColorSpan::class.java)
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals(7, string.getSpanStart(span))
        assertEquals(12, string.getSpanEnd(span))
        assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE, string.getSpanFlags(span))
        assertEquals(Color.BLUE, span.foregroundColor)
    }

    @Test
    fun testThatItSetsAGroupSpan() {
        // given
        val sut = TextWriter()
        sut.write("hello, world!")

        // when
        val someGroupSpan = object : InlineSpan() {
            init { add(ForegroundColorSpan(Color.BLUE)); add(BackgroundColorSpan(Color.RED)) }
            override fun toNode(literal: String?): Node = Text()
        }

        sut.set(someGroupSpan, 7, 12)

        // then
        val string = sut.spannableString

        // it sets the group span
        val groupSpans = string.getSpans(0, 13, GroupSpan::class.java)
        assertEquals(1, groupSpans.size)

        val groupSpan = groupSpans.first()
        assertEquals(7, string.getSpanStart(groupSpan))
        assertEquals(12, string.getSpanEnd(groupSpan))
        assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE, string.getSpanFlags(groupSpan))

        // it sets the first span in the group
        val foregroundColorSpans = string.getSpans(0, 12, ForegroundColorSpan::class.java)
        assertEquals(1, foregroundColorSpans.size)

        val foregroundSpan = foregroundColorSpans.first()
        assertEquals(7, string.getSpanStart(foregroundSpan))
        assertEquals(12, string.getSpanEnd(foregroundSpan))
        assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE, string.getSpanFlags(foregroundSpan))
        assertEquals(Color.BLUE, foregroundSpan.foregroundColor)

        // it sets the second span in the group
        val backgroundColorSpans = string.getSpans(0, 12, BackgroundColorSpan::class.java)
        assertEquals(1, backgroundColorSpans.size)

        val backgroundSpan = backgroundColorSpans.first()
        assertEquals(7, string.getSpanStart(backgroundSpan))
        assertEquals(12, string.getSpanEnd(backgroundSpan))
        assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE, string.getSpanFlags(backgroundSpan))
        assertEquals(Color.RED, backgroundSpan.backgroundColor)
    }
}

