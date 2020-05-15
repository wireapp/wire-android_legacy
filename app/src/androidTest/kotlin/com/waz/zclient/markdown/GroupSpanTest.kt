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
import android.graphics.Typeface
import android.text.style.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waz.zclient.markdown.spans.GroupSpan
import com.waz.zclient.markdown.spans.commonmark.*
import com.waz.zclient.markdown.spans.commonmark.ImageSpan
import com.waz.zclient.markdown.spans.custom.CustomQuoteSpan
import com.waz.zclient.markdown.spans.custom.MarkdownLinkSpan
import com.waz.zclient.markdown.spans.custom.ParagraphSpacingSpan
import org.commonmark.node.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class GroupSpanTest {

    @Test
    fun testThatItConfiguresSpansAndNode_Document() {
        // given
        val sut = DocumentSpan()

        // then
        assertEquals(GroupSpan.Priority.HIGH, sut.priority)
        assertTrue(sut.spans.isEmpty())
        assertTrue(sut.toNode() is Document)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_Heading() {
        // given
        val sut = HeadingSpan(1, 2f, 16, 8)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(3, spans.size)

        for (span in spans) {
            when (span) {
                is RelativeSizeSpan -> {
                    assertEquals(2f, span.sizeChange)
                }
                is ParagraphSpacingSpan -> {
                    assertEquals(16, span.before)
                    assertEquals(8, span.after)
                }
                is StyleSpan -> {
                    assertEquals(Typeface.BOLD, span.style)
                }
                else -> fail()
            }
        }

        val node = sut.toNode()
        assertTrue(node is Heading)
        assertEquals(1, (node as Heading).level)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_Paragraph() {
        // given
        val sut = ParagraphSpan(16, 8)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(1, spans.size)
        val span = spans.first()

        when (span) {
            is ParagraphSpacingSpan -> {
                assertEquals(16, span.before)
                assertEquals(8, span.after)
            }
            else -> fail()
        }

        assertTrue(sut.toNode() is Paragraph)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_BlockQuote() {
        // given
        val sut = BlockQuoteSpan(Color.BLUE, Color.GREEN, 4, 8, 16, 8)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(3, spans.size)

        for (span in spans) {
            when (span) {
                is CustomQuoteSpan -> {
                    assertEquals(Color.GREEN, span.color)
                    assertEquals(4, span.stripeWidthSize)
                    assertEquals(8, span.gapWidthSize)
                }
                is ParagraphSpacingSpan -> {
                    assertEquals(16, span.before)
                    assertEquals(8, span.after)
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.BLUE, span.foregroundColor)
                }
                else -> fail()
            }
        }

        assertTrue(sut.toNode() is BlockQuote)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_OrderedList() {
        // given
        val sut = OrderedListSpan(3)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)
        assertTrue(sut.spans.isEmpty())

        val node = sut.toNode()
        assertTrue(node is OrderedList)
        assertEquals(3, (node as OrderedList).startNumber)
        assertEquals('.', (node).delimiter)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_BulletList() {
        // given
        val sut = BulletListSpan('+')

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)
        assertTrue(sut.spans.isEmpty())

        val node = sut.toNode()
        assertTrue(node is BulletList)
        assertEquals('+', (node as BulletList).bulletMarker)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_ListItem() {
        // given
        val sut = ListItemSpan()

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)
        assertTrue(sut.spans.isEmpty())

        assertTrue(sut.toNode() is ListItem)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_FencedCodeBlock() {
        // given
        val sut = FencedCodeBlockSpan(Color.RED, 16, 8, 20)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(4, spans.size)

        for (span in spans) {
            when (span) {
                is TypefaceSpan -> {
                    assertEquals("monospace", span.family)
                }
                is LeadingMarginSpan -> {
                    assertEquals(16, span.getLeadingMargin(true))
                    assertEquals(16, span.getLeadingMargin(false))
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.RED, span.foregroundColor)
                }
                is ParagraphSpacingSpan -> {
                    assertEquals(8, span.before)
                    assertEquals(20, span.after)
                }
                else -> fail()
            }
        }

        val node = sut.toNode("hello")
        assertTrue(node is FencedCodeBlock)
        val codeBlock = node as FencedCodeBlock
        assertEquals("hello", codeBlock.literal)
        assertEquals('`', codeBlock.fenceChar)
        assertEquals(3, codeBlock.fenceLength)
        assertEquals(4, codeBlock.fenceIndent)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_IndentedCodeBlock() {
        // given
        val sut = IndentedCodeBlockSpan(Color.YELLOW, 20, 8, 16)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(4, spans.size)

        for (span in spans) {
            when (span) {
                is TypefaceSpan -> {
                    assertEquals("monospace", span.family)
                }
                is LeadingMarginSpan -> {
                    assertEquals(20, span.getLeadingMargin(true))
                    assertEquals(20, span.getLeadingMargin(false))
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.YELLOW, span.foregroundColor)
                }
                is ParagraphSpacingSpan -> {
                    assertEquals(8, span.before)
                    assertEquals(16, span.after)
                }
                else -> fail()
            }
        }

        val node = sut.toNode("hello")
        assertTrue(node is IndentedCodeBlock)
        assertEquals("hello", (node as IndentedCodeBlock).literal)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_HtmlBlock() {
        // given
        val sut = HtmlBlockSpan(Color.GREEN, 8, 16, 20)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(4, spans.size)

        for (span in spans) {
            when (span) {
                is TypefaceSpan -> {
                    assertEquals("monospace", span.family)
                }
                is LeadingMarginSpan -> {
                    assertEquals(8, span.getLeadingMargin(true))
                    assertEquals(8, span.getLeadingMargin(false))
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.GREEN, span.foregroundColor)
                }
                is ParagraphSpacingSpan -> {
                    assertEquals(16, span.before)
                    assertEquals(20, span.after)
                }
                else -> fail()
            }
        }

        val node = sut.toNode("hello")
        assertTrue(node is HtmlBlock)
        assertEquals("hello", (node as HtmlBlock).literal)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_Link() {
        // given
        val url = "www.wire.com"
        val onClickHandler: (String) -> Unit = { s -> assertEquals(url, s) }
        val sut = LinkSpan(url, Color.BLUE, onClickHandler)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(2, spans.size)

        for (span in spans) {
            when (span) {
                is MarkdownLinkSpan -> {
                    assertEquals(url, span.url)
                    assertEquals(onClickHandler, span.onClick)
                    span.onClick(url)
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.BLUE, span.foregroundColor)
                }
                else -> fail()
            }
        }

        val node = sut.toNode()
        assertTrue(node is Link)
        assertEquals(url, (node as Link).destination)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_Image() {
        // given
        val url = "www.wire.com/example.jpg"
        val onClickHandler: (String) -> Unit = { s -> assertEquals(url, s) }
        val sut = ImageSpan(url, Color.BLUE, onClickHandler)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(2, spans.size)

        for (span in spans) {
            when (span) {
                is MarkdownLinkSpan -> {
                    assertEquals(url, span.url)
                    assertEquals(onClickHandler, span.onClick)
                    span.onClick(url)
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.BLUE, span.foregroundColor)
                }
                else -> fail()
            }
        }

        val node = sut.toNode()
        assertTrue(node is Image)
        assertEquals(url, (node as Image).destination)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_Emphasis() {
        // given
        val sut = EmphasisSpan()

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(1, spans.size)

        val span = spans.first()
        assertTrue(span is StyleSpan)
        assertEquals(Typeface.ITALIC, (span as StyleSpan).style)

        val node = sut.toNode()
        assertTrue(node is Emphasis)
        assertEquals("_", (node as Emphasis).openingDelimiter)
        assertEquals("_", (node).closingDelimiter)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_StrongEmphasis() {
        // given
        val sut = StrongEmphasisSpan()

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(1, spans.size)

        val span = spans.first()
        assertTrue(span is StyleSpan)
        assertEquals(Typeface.BOLD, (span as StyleSpan).style)

        val node = sut.toNode()
        assertTrue(node is StrongEmphasis)
        assertEquals("**", (node as StrongEmphasis).openingDelimiter)
        assertEquals("**", (node).closingDelimiter)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_Code() {
        // given
        val sut = CodeSpan(Color.CYAN)

        // then
        assertEquals(GroupSpan.Priority.LOW, sut.priority)

        val spans = sut.spans
        assertEquals(2, spans.size)

        for (span in spans) {
            when (span) {
                is TypefaceSpan -> {
                    assertEquals("monospace", span.family)
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.CYAN, span.foregroundColor)
                }
                else -> fail()
            }
        }

        val node = sut.toNode("hello")
        assertTrue(node is Code)
        assertEquals("hello", (node as Code).literal)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_HtmlInline() {
        // given
        val sut = HtmlInlineSpan(Color.BLUE)

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)

        val spans = sut.spans
        assertEquals(2, spans.size)

        for (span in spans) {
            when (span) {
                is TypefaceSpan -> {
                    assertEquals("monospace", span.family)
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.BLUE, span.foregroundColor)
                }
                else -> fail()
            }
        }

        val node = sut.toNode("em")
        assertTrue(node is HtmlInline)
        assertEquals("em", (node as HtmlInline).literal)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_Text() {
        // given
        val sut = TextSpan(20, Color.GREEN)

        // then
        assertEquals(GroupSpan.Priority.LOW, sut.priority)

        val spans = sut.spans
        assertEquals(2, spans.size)

        for (span in spans) {
            when (span) {
                is AbsoluteSizeSpan -> {
                    assertEquals(20, span.size)
                }
                is ForegroundColorSpan -> {
                    assertEquals(Color.GREEN, span.foregroundColor)
                }
                else -> fail()
            }
        }

        val node = sut.toNode("hello")
        assertTrue(node is Text)
        assertEquals("hello", (node as Text).literal)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_SoftLineBreak() {
        // given
        val sut = SoftLineBreakSpan()

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)
        assertTrue(sut.spans.isEmpty())
        assertTrue(sut.toNode() is SoftLineBreak)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_HardLineBreak() {
        // given
        val sut = HardLineBreakSpan()

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)
        assertTrue(sut.spans.isEmpty())
        assertTrue(sut.toNode() is HardLineBreak)
    }

    @Test
    fun testThatItConfiguresSpansAndNode_ThematicBreak() {
        // given
        val sut = ThematicBreakSpan()

        // then
        assertEquals(GroupSpan.Priority.MEDIUM, sut.priority)
        assertTrue(sut.spans.isEmpty())
        assertTrue(sut.toNode() is ThematicBreak)
    }
}
