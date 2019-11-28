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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waz.zclient.markdown.spans.commonmark.*
import org.commonmark.node.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StyleSheetTest {

    val sut = StyleSheet()

    // ---------------------------------------------------------------------------

    @Test
    fun testThatItConfiguresGroupSpan_Heading() {
        // given
        for (i in 1 until 6) {
            val heading = Heading()
            heading.level = i

            // when
            val result = sut.spanFor(heading)

            // then
            assertTrue(result is HeadingSpan)
            val span = result as HeadingSpan

            assertEquals(i, span.level)
            assertEquals(sut.headingSizeMultipliers[i] ?: 1f, span.fontSizeMultiplier)
            assertEquals(sut.paragraphSpacingBefore, span.beforeSpacing)
            assertEquals(sut.paragraphSpacingAfter, span.afterSpacing)
        }
    }

    @Test
    fun testThatItConfiguresGroupSpan_Paragraph_Outer() {
        // given
        val paragraph = Paragraph()

        // when
        val result = sut.spanFor(paragraph)

        // then
        assertTrue(result is ParagraphSpan)
        val span = result as ParagraphSpan

        assertEquals(sut.paragraphSpacingBefore, span.beforeSpacing)
        assertEquals(sut.paragraphSpacingAfter, span.afterSpacing)
    }

    @Test
    fun testThatItConfiguresGroupSpan_Paragraph_Nested() {
        // given
        val parent = ListItem()
        val paragraph = Paragraph()
        parent.appendChild(paragraph)

        // when
        val result = sut.spanFor(paragraph)

        // then
        assertTrue(result is ParagraphSpan)
        val span = result as ParagraphSpan

        assertEquals(0, span.beforeSpacing)
        assertEquals(0, span.afterSpacing)
    }

    @Test
    fun testThatItConfiguresGroupSpan_BlockQuote() {
        // given
        val quote = BlockQuote()

        // when
        val result = sut.spanFor(quote)

        // then
        assertTrue(result is BlockQuoteSpan)
        val span = result as BlockQuoteSpan

        assertEquals(sut.quoteColor, span.color)
        assertEquals(sut.quoteStripeColor, span.stripeColor)
        assertEquals(sut.quoteStripeWidth, span.stripeWidth)
        assertEquals(sut.quoteGapWidth, span.gapWidth)
        assertEquals(sut.quoteSpacingBefore, span.beforeSpacing)
        assertEquals(sut.quoteSpacingAfter, span.afterSpacing)
    }

    @Test
    fun testThatItConfiguresGroupSpan_OrderedList() {
        // given
        val list = OrderedList()
        list.startNumber = 3

        // when
        val result = sut.spanFor(list)

        // then
        assertTrue(result is OrderedListSpan)
        assertEquals(3, (result as OrderedListSpan).startNumber)
    }

    @Test
    fun testThatItConfiguresGroupSpan_BulletList() {
        // given
        val list = BulletList()
        list.bulletMarker = '*'

        // when
        val result = sut.spanFor(list)

        // then
        assertTrue(result is BulletListSpan)
        assertEquals('*', (result as BulletListSpan).marker)
    }

    @Test
    fun testThatItConfiguresGroupSpan_ListItem() {
        // given
        val item = ListItem()

        // when
        val result = sut.spanFor(item)

        // then
        assertTrue(result is ListItemSpan)
    }

    @Test
    fun testThatItConfiguresGroupSpan_FencedCodeBlock() {
        // given
        val fencedCode = FencedCodeBlock()

        // when
        val result = sut.spanFor(fencedCode)

        // then
        assertTrue(result is FencedCodeBlockSpan)
        val span = result as FencedCodeBlockSpan

        assertEquals(sut.codeColor, span.color)
        assertEquals((sut.codeBlockIndentation * sut.screenDensity).toInt(), span.indentation)
        assertEquals(sut.paragraphSpacingBefore, span.beforeSpacing)
        assertEquals(sut.paragraphSpacingAfter, span.afterSpacing)
    }

    @Test
    fun testThatItConfiguresGroupSpan_IndentedCodeBlock() {
        // given
        val indentedCode = IndentedCodeBlock()

        // when
        val result = sut.spanFor(indentedCode)

        // then
        assertTrue(result is IndentedCodeBlockSpan)
        val span = result as IndentedCodeBlockSpan

        assertEquals(sut.codeColor, span.color)
        assertEquals((sut.codeBlockIndentation * sut.screenDensity).toInt(), span.indentation)
        assertEquals(sut.paragraphSpacingBefore, span.beforeSpacing)
        assertEquals(sut.paragraphSpacingAfter, span.afterSpacing)
    }

    @Test
    fun testThatItConfiguresGroupSpan_HtmlBlock() {
        // given
        val htmlBlock = HtmlBlock()

        // when
        val result = sut.spanFor(htmlBlock)

        // then
        assertTrue(result is HtmlBlockSpan)
        val span = result as HtmlBlockSpan

        assertEquals(sut.codeColor, span.color)
        assertEquals((sut.codeBlockIndentation * sut.screenDensity).toInt(), span.indentation)
        assertEquals(sut.paragraphSpacingBefore, span.beforeSpacing)
        assertEquals(sut.paragraphSpacingAfter, span.afterSpacing)
    }

    @Test
    fun testThatItConfiguresGroupSpan_Link() {
        // given
        val link = Link("www.wire.com", "")

        // when
        val result = sut.spanFor(link)

        // then
        assertTrue(result is LinkSpan)
        val span = result as LinkSpan

        assertEquals("www.wire.com", span.url)
        assertEquals(sut.linkColor, span.color)
    }

    @Test
    fun testThatItConfiguresGroupSpan_Image() {
        // given
        val image = Image("www.wire.com", "")

        // when
        val result = sut.spanFor(image)

        // then
        assertTrue(result is ImageSpan)
        assertEquals("www.wire.com", (result as ImageSpan).url)
    }

    @Test
    fun testThatItConfiguresGroupSpan_Emphasis() {
        // given
        val emphasis = Emphasis()

        // when
        val result = sut.spanFor(emphasis)

        // then
        assertTrue(result is EmphasisSpan)
    }

    @Test
    fun testThatItConfiguresGroupSpan_StrongEmphasis() {
        // given
        val strongEmphasis = StrongEmphasis()

        // when
        val result = sut.spanFor(strongEmphasis)

        // then
        assertTrue(result is StrongEmphasisSpan)
    }

    @Test
    fun testThatItConfiguresGroupSpan_Code() {
        // given
        val code = Code()

        // when
        val result = sut.spanFor(code)

        // then
        assertTrue(result is CodeSpan)
        assertEquals(sut.codeColor, (result as CodeSpan).color)
    }

    @Test
    fun testThatItConfiguresGroupSpan_HtmlInline() {
        // given
        val html = HtmlInline()

        // when
        val result = sut.spanFor(html)

        // then
        assertTrue(result is HtmlInlineSpan)
        assertEquals(sut.codeColor, (result as HtmlInlineSpan).color)
    }

    @Test
    fun testThatItConfiguresGroupSpan_Text() {
        // given
        val text = Text()

        // when
        val result = sut.spanFor(text)

        // then
        assertTrue(result is TextSpan)
        val span = result as TextSpan

        assertEquals(sut.baseFontSize, span.size)
        assertEquals(sut.baseFontColor, span.color)
    }

    @Test
    fun testThatItConfiguresGroupSpan_SoftLineBreak() {
        // given
        val lineBreak = SoftLineBreak()

        // when
        val result = sut.spanFor(lineBreak)

        // then
        assertTrue(result is SoftLineBreakSpan)
    }

    @Test
    fun testThatItConfiguresGroupSpan_HardLineBreak() {
        // given
        val lineBreak = HardLineBreak()

        // when
        val result = sut.spanFor(lineBreak)

        // then
        assertTrue(result is HardLineBreakSpan)
    }

    @Test
    fun testThatItConfiguresGroupSpan_ThematicBreak() {
        // given
        val lineBreak = ThematicBreak()

        // when
        val result = sut.spanFor(lineBreak)

        // then
        assertTrue(result is ThematicBreakSpan)
    }
}
