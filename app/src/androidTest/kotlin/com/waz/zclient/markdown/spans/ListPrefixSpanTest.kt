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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.SpannableString
import android.text.Spanned
import com.waz.zclient.markdown.spans.custom.ListPrefixSpan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito.*

class ListPrefixSpanTest {

    private val digitWidth = 10
    private val color = Color.RED

    private val canvas = mock(Canvas::class.java)
    private val paint = mock(Paint::class.java)
    private val text = SpannableString("123")

    private val c1Width = 5f
    private val c2Width = 10f
    private val c3Width = 8f

    @Before
    fun setup() {
        // define some arbitrary widths of each digit
        `when`(paint.measureText(text, 0, 1)).thenReturn(c1Width)
        `when`(paint.measureText(text, 1, 2)).thenReturn(c2Width)
        `when`(paint.measureText(text, 2, 3)).thenReturn(c3Width)
        `when`(paint.color).thenReturn(Color.GREEN)
    }

    @Test
    fun testThatItDraws() {
        // given
        val sut = ListPrefixSpan(text.length, digitWidth, color)
        text.setSpan(sut, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // some arbitrary values
        val x = 0f; val y = 0; val top = -10; val bottom = 10

        // expected values: see ListPrefixSpan implmentation for details
        val baseOffset = 0f
        val c1XPos = x + baseOffset + (0 * digitWidth) + (digitWidth - c1Width)/2f
        val c2XPos = x + baseOffset + (1 * digitWidth) + (digitWidth - c2Width)/2f
        val c3XPos = x + baseOffset + (2 * digitWidth) + (digitWidth - c3Width)/2f

        // when
        sut.draw(canvas, text, 0, text.length, x, top, y, bottom, paint)

        // then check correct methods called in correct order
        val inOrder = inOrder(paint, canvas)

        // sets the color
        inOrder.verify(paint).color = color

        // three calls to canvas (one for each digit)
        inOrder.verify(canvas).drawText(eq(text), eq(0), eq(1), eq(c1XPos), eq(y.toFloat()), eq(paint))
        inOrder.verify(canvas).drawText(eq(text), eq(1), eq(2), eq(c2XPos), eq(y.toFloat()), eq(paint))
        inOrder.verify(canvas).drawText(eq(text), eq(2), eq(3), eq(c3XPos), eq(y.toFloat()), eq(paint))

        // resets the original color
        inOrder.verify(paint).color = eq(Color.GREEN)
    }

    @Test
    fun testThatItDrawsRightAligned() {
        // given that the span should have space for 2 extra digits
        val digits = text.length + 2
        val sut = ListPrefixSpan(digits, digitWidth, color)
        text.setSpan(sut, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // some arbitrary values
        val x = 0f; val y = 0; val top = -10; val bottom = 10

        // expected values: see ListPrefixSpan implmentation for details
        val totalWidth = digits * digitWidth
        val baseOffset = totalWidth - (text.length * digitWidth)
        val c1XPos = x + baseOffset + (0 * digitWidth) + (digitWidth - c1Width)/2f
        val c2XPos = x + baseOffset + (1 * digitWidth) + (digitWidth - c2Width)/2f
        val c3XPos = x + baseOffset + (2 * digitWidth) + (digitWidth - c3Width)/2f

        // when
        sut.draw(canvas, text, 0, text.length, x, top, y, bottom, paint)

        // then check correct methods called in correct order
        val inOrder = inOrder(paint, canvas)

        // sets the color
        inOrder.verify(paint).color = color

        // three calls to canvas (one for each digit)
        inOrder.verify(canvas).drawText(eq(text), eq(0), eq(1), eq(c1XPos), eq(y.toFloat()), eq(paint))
        inOrder.verify(canvas).drawText(eq(text), eq(1), eq(2), eq(c2XPos), eq(y.toFloat()), eq(paint))
        inOrder.verify(canvas).drawText(eq(text), eq(2), eq(3), eq(c3XPos), eq(y.toFloat()), eq(paint))

        // resets the original color
        inOrder.verify(paint).color = eq(Color.GREEN)
    }

    @Test
    fun testThatItDoesNotDrawWhenNoTextAttached() {
        // given
        val sut = ListPrefixSpan(text.length, digitWidth, color)

        // when
        sut.draw(canvas, text, 0, text.length, 0f, 0, 0, 0, paint)

        // then no drawing methods called
        verifyZeroInteractions(paint)
        verifyZeroInteractions(canvas)
    }
}
