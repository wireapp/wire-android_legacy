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

import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.LeadingMarginSpan
import android.text.style.TabStopSpan
import com.waz.zclient.markdown.StyleSheet
import com.waz.zclient.markdown.spans.custom.ListPrefixSpan
import com.waz.zclient.markdown.spans.custom.ParagraphSpacingSpan
import com.waz.zclient.markdown.utils.*
import org.commonmark.internal.renderer.text.BulletListHolder
import org.commonmark.internal.renderer.text.ListHolder
import org.commonmark.node.*
import org.commonmark.renderer.NodeRenderer

/**
 * A SpanRenderer instance traverses the syntax tree of a markdown document and constructs
 * a spannable string with the appropriate style spans for each markdown unit. The style
 * spans for each node in the tree are provided by a configured StyleSheet instance.
 *
 * NOTE: Because cursor positions are stored on a stack within TextWriter, it is critical that
 * every call to save the cursor position must have a matching call to retrieve it. Unbalanced
 * calls will lead to incorrect rendering.
 */
class SpanRenderer(private val styleSheet: StyleSheet) : AbstractVisitor(), NodeRenderer {

    val softBreaksAsHardBreaks = true
    val spannableString: SpannableString get() = writer.spannableString.trim() as SpannableString

    private val writer = TextWriter()
    private var listHolder: ListHolder? = null
    private var listRanges = mutableListOf<IntRange>()

    //region NodeRenderer
    override fun getNodeTypes(): MutableSet<Class<out Node>> {
        return mutableSetOf(
            Document::class.java,
            Heading::class.java,
            Paragraph::class.java,
            BlockQuote::class.java,
            OrderedList::class.java,
            BulletList::class.java,
            ListItem::class.java,
            FencedCodeBlock::class.java,
            IndentedCodeBlock::class.java,
            HtmlBlock::class.java,
            Link::class.java,
            Image::class.java,
            Emphasis::class.java,
            StrongEmphasis::class.java,
            Code::class.java,
            HtmlInline::class.java,
            Text::class.java,
            SoftLineBreak::class.java,
            HardLineBreak::class.java,
            ThematicBreak::class.java
        )
    }

    override fun render(node: Node?) { node?.accept(this) }
    //endregion

    override fun visit(document: Document?) {
        if (document == null) return
        visitChildren(document)
    }

    override fun visit(heading: Heading?) {
        if (heading == null) return
        writer.saveCursor()
        visitChildren(heading)
        writeLineIfNeeded(heading)
        writer.set(styleSheet.spanFor(heading), writer.retrieveCursor())
    }

    override fun visit(paragraph: Paragraph?) {
        if (paragraph == null) return
        writer.saveCursor()
        visitChildren(paragraph)
        writeLineIfNeeded(paragraph)
        writer.set(styleSheet.spanFor(paragraph), writer.retrieveCursor())
    }

    override fun visit(blockQuote: BlockQuote?) {
        if (blockQuote == null) return
        if (blockQuote.isNested) writer.line()
        writer.saveCursor()
        visitChildren(blockQuote)
        writeLineIfNeeded(blockQuote)

        val start = writer.retrieveCursor()
        if (blockQuote.isOuterMost)
            writer.set(styleSheet.spanFor(blockQuote), start)
    }

    override fun visit(orderedList: OrderedList?) {
        if (orderedList == null) return
        writer.saveCursor()

        // we're already inside a list
        if (listHolder != null) writer.line()

        // new holder for this list
        listHolder = SmartOrderedListHolder(listHolder, orderedList)
        visitChildren(orderedList)
        writeLineIfNeeded(orderedList)

        val start = writer.retrieveCursor()
        val span = styleSheet.spanFor(orderedList)

        writer.set(span, start)

        // we're done with the current holder
        listHolder = (listHolder as ListHolder).parent
        listRanges.add(start..writer.cursor)
    }

    override fun visit(bulletList: BulletList?) {
        if (bulletList == null) return
        writer.saveCursor()

        // we're already inside a list
        if (listHolder != null) writer.line()

        // new holder for this list
        listHolder = BulletListHolder(listHolder, bulletList)
        visitChildren(bulletList)
        writeLineIfNeeded(bulletList)

        val start = writer.retrieveCursor()
        val span = styleSheet.spanFor(bulletList)

        writer.set(span, start)

        listHolder = (listHolder as ListHolder).parent
        listRanges.add(start..writer.cursor)
    }

    override fun visit(listItem: ListItem?) {
        if (listItem == null) return
        val prefixStart = writer.saveCursor()

        var digits = 3
        var digitWidth = styleSheet.maxDigitWidth.toInt()

        val standardPrefixWidth = styleSheet.listItemContentMargin
        var tabLocation = standardPrefixWidth
        val indentation = (listHolder?.depth ?: 0) * standardPrefixWidth

        when (listHolder) {
            is SmartOrderedListHolder -> {
                val smartListHolder = listHolder as SmartOrderedListHolder

                // standard prefix width is 2 digits + "."
                digits = Math.max(2, smartListHolder.largestPrefix.numberOfDigits) + 1
                tabLocation = digits * digitWidth + styleSheet.listPrefixGapWidth

                writer.write("${smartListHolder.counter}.")
                smartListHolder.increaseCounter()
            }

            is BulletListHolder -> {
                // a bullet is just one digit, but we make the width equal to 3
                // so that it aligns with number prefixes
                digits = 1
                digitWidth = 3 * styleSheet.maxDigitWidth.toInt()

                writer.write("\u2022")
            }
        }

        // span the prefix
        val prefixSpan = ListPrefixSpan(digits, digitWidth, styleSheet.listPrefixColor)
        writer.set(AbsoluteSizeSpan(styleSheet.baseFontSize, false), prefixStart, writer.cursor)
        writer.set(prefixSpan, prefixStart, writer.cursor)

        // write the content
        writer.tabIfNeeded()
        visitChildren(listItem)
        writeLineIfNeeded(listItem)

        /*
            Here comes the tricky part: To make sure that the indentation of a list item is correct,
            we need to consider some cases. There are potentially three different indentations
            required. The plain text list on the left should render like the list on the right:

            1. first line                   1. first line
            line after softbreak    ->         line after softbreak
            - nested list                      - nested list

            Suppose the margin is 10 points. The 10p tabstop ensures the 'f' is positioned 10p to
            the right from the left side. The first line therefore needs 0p indentation for its
            first line, but 10p for the rest of its lines in its paragraph (so wrapped text aligns
            with the 'f'). The second line starts a new paragraph but has no tabstop, so it needs
            10p indentation for all its lines. The nested list needs to be indented to align with
            the 'f' too, but this is taken care of when that list is itself rendered.

            So, we need to check if this list item contains a break, and if so, whether that break
            comes from a softbreak or a nested list. We apply the first type of indentation to the
            first paragraph and the second type of indentation from the second paragraph up to the
            nested list (or to the end of the list item if no nested list exists).
         */


        val start = writer.retrieveCursor()
        val last = writer.cursor - 1
        val startOfSecondParagraph: Int?
        val startOfNestedList: Int?

        val breakIndex = writer.toString().indexOf('\n', start)

        // break exists and is not last char in item
        startOfSecondParagraph = if (breakIndex in start until last) breakIndex else null

        // sort by start index ascending
        listRanges.sortBy { it.start }

        startOfNestedList = listRanges.firstOrNull {
            it.start > start && it.endInclusive <= writer.cursor
        }?.start

        var b1 = writer.cursor
        var b2: Int? = null

        when {
            // we need to add 1 to b1 because startOfSecondParagraph and startOfNestedList
            // contain the index of the newline preceeding the paragraph/nestedlist. Adding 1
            // simply includes the newline in the span (necessary for paragraph spans).
            startOfSecondParagraph != null && startOfNestedList == null -> {
                // up to second paragraph, then to end
                b1 = startOfSecondParagraph + 1
                b2 = writer.cursor
            }
            startOfSecondParagraph == null && startOfNestedList != null -> {
                // up to nest list only
                b1 = startOfNestedList + 1
                b2 = null
            }
            startOfSecondParagraph != null && startOfNestedList != null -> {
                if (startOfSecondParagraph == startOfNestedList) {
                    // up to nest list only
                    b1 = startOfNestedList + 1
                    b2 = null
                } else {
                    // up to second paragraph, then to nested list
                    b1 = startOfSecondParagraph + 1
                    b2 = startOfNestedList
                }
            }
        }

        // indentation for first paragraph
        writer.set(LeadingMarginSpan.Standard(indentation, indentation + standardPrefixWidth), start, b1)
        writer.set(TabStopSpan.Standard(tabLocation), start, b1)

        if (b2 != null) {
            // indentation for second paragraph up to nested list or item end
            writer.set(LeadingMarginSpan.Standard(indentation + standardPrefixWidth), b1, b2)
        }

        // before and after spacing, from item start up to nested list or item end
        writer.set(ParagraphSpacingSpan(styleSheet.listItemSpacingBefore, styleSheet.listItemSpacingAfter), start, b2 ?: b1)

        // finally, the span for the whole item
        writer.set(styleSheet.spanFor(listItem), start)
    }

    override fun visit(fencedCodeBlock: FencedCodeBlock?) {
        if (fencedCodeBlock == null) return
        writer.saveCursor()
        writer.write(fencedCodeBlock.literal)
        writer.set(styleSheet.spanFor(fencedCodeBlock), writer.retrieveCursor())
    }

    override fun visit(indentedCodeBlock: IndentedCodeBlock?) {
        if (indentedCodeBlock == null) return
        writer.saveCursor()
        writer.write(indentedCodeBlock.literal)
        writer.set(styleSheet.spanFor(indentedCodeBlock), writer.retrieveCursor())
    }

    override fun visit(htmlBlock: HtmlBlock?) {
        if (htmlBlock == null) return
        writer.saveCursor()
        writer.write(htmlBlock.literal)
        writeLineIfNeeded(htmlBlock)
        writer.set(styleSheet.spanFor(htmlBlock), writer.retrieveCursor())
    }

    override fun visit(link: Link?) {
        if (link == null) return
        writer.saveCursor()
        visitChildren(link)
        writer.set(styleSheet.spanFor(link), writer.retrieveCursor())
    }

    override fun visit(image: Image?) {
        if (image == null) return
        writer.saveCursor()
        visitChildren(image)
        writer.set(styleSheet.spanFor(image), writer.retrieveCursor())
    }

    override fun visit(emphasis: Emphasis?) {
        if (emphasis == null) return
        writer.saveCursor()
        visitChildren(emphasis)
        writer.set(styleSheet.spanFor(emphasis), writer.retrieveCursor())
    }

    override fun visit(strongEmphasis: StrongEmphasis?) {
        if (strongEmphasis == null) return
        writer.saveCursor()
        visitChildren(strongEmphasis)
        writer.set(styleSheet.spanFor(strongEmphasis), writer.retrieveCursor())
    }

    override fun visit(code: Code?) {
        if (code == null) return
        writer.saveCursor()
        writer.write(code.literal)
        writer.set(styleSheet.spanFor(code), writer.retrieveCursor())
    }

    override fun visit(htmlInline: HtmlInline?) {
        if (htmlInline == null) return
        writer.saveCursor()
        writer.write(htmlInline.literal)
        writer.set(styleSheet.spanFor(htmlInline), writer.retrieveCursor())
    }

    override fun visit(text: Text?) {
        if (text == null) return
        writer.saveCursor()
        writer.write(text.literal)
        writer.set(styleSheet.spanFor(text), writer.retrieveCursor())
    }

    override fun visit(softLineBreak: SoftLineBreak?) {
        if (softLineBreak == null) return
        writer.saveCursor()
        if (softBreaksAsHardBreaks) writer.line() else writer.space()
        writer.set(styleSheet.spanFor(softLineBreak), writer.retrieveCursor())
    }

    override fun visit(hardLineBreak: HardLineBreak?) {
        if (hardLineBreak == null) return
        writer.saveCursor()
        writer.line()
        writer.set(styleSheet.spanFor(hardLineBreak), writer.retrieveCursor())
    }

    override fun visit(thematicBreak: ThematicBreak?) {
        if (thematicBreak == null) return
        writer.saveCursor()
        writer.write("---\n")
        writer.set(styleSheet.spanFor(thematicBreak), writer.retrieveCursor())
    }

    private fun writeLineIfNeeded(node: Node) {
        when (node) {
        // newlines only for non nested paragraphs
            is Paragraph -> if (!node.isOuterMost) return
            else -> { }
        }
        
        writer.lineIfNeeded()
    }
}
