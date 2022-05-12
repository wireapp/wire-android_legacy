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

import android.graphics.Paint
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import com.waz.zclient.markdown.spans.custom.ParagraphSpacingSpan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class ParagraphSpacingSpanTest {

    private val paint = mock(TextPaint::class.java)
    private val text = SpannableString("while she was able to see, her eyes were on mine.")

    @Before
    fun setup() {
        // this span is density sensitive
        paint.density = 2f
    }

    fun check(fm: Paint.FontMetricsInt, ascent: Int, top: Int, descent: Int, bottom: Int, leading: Int) {
        val scale = { v: Int -> (paint.density * v).toInt() }
        assertEquals(scale(ascent), fm.ascent)
        assertEquals(scale(top), fm.top)
        assertEquals(scale(descent), fm.descent)
        assertEquals(scale(bottom), fm.bottom)
        assertEquals(scale(leading), fm.leading)
    }

    @Test
    fun testTHatItChoosesHeightForSingleLine() {
        // given
        val sut = ParagraphSpacingSpan(8, 16)
        text.setSpan(sut, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // all values initialized to zero
        val fm = Paint.FontMetricsInt()

        // when first line chooses height: 'while she was '
        sut.chooseHeight(text, 0, text.length, 0, 0, fm, paint)

        // then the top of the line is 8pts higher and the bottom of the line is 16pts lower
        check(fm, -8, -8, 16, 16, 0)
    }

    @Test
    fun testThatItChoosesHeightForMultipleLines() {
        // given
        val sut = ParagraphSpacingSpan(8, 16)
        text.setSpan(sut, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // all values initialized to zero
        val fm = Paint.FontMetricsInt()

        // when first line chooses height: 'while she was '
        sut.chooseHeight(text, 0, 14, 0, 0, fm, paint)

        // then the top of the line is 8pts higher
        check(fm, -8, -8, 0, 0, 0)

        // when second line chooses height: 'able to see, '
        sut.chooseHeight(text, 14, 27, 0, 0, fm, paint)

        // then original fm is used
        check(fm, 0, 0, 0, 0, 0)

        // when last line chooses height: 'her eyes were on mine.'
        sut.chooseHeight(text, 27, 49, 0, 0, fm, paint)

        // then the bottom of the last line is 16pts lower
        check(fm, 0, 0, 16, 16, 0)
    }

    @Test
    fun testThatItDoesNotChangeFontMetricsIfNotAttachedToText() {
        // given
        val sut = ParagraphSpacingSpan(8, 16)

        // all values initialized to zero
        val fm = Paint.FontMetricsInt()

        // when first line chooses height: 'while she was '
        sut.chooseHeight(text, 0, text.length, 0, 0, fm, paint)

        // then the fm is unchanged
        check(fm, 0, 0, 0, 0, 0)
    }
}
