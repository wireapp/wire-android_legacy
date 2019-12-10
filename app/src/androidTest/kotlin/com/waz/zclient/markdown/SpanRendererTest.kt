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

import android.text.SpannableString
import android.text.style.LeadingMarginSpan
import android.text.style.TabStopSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waz.zclient.markdown.spans.GroupSpan
import com.waz.zclient.markdown.spans.commonmark.*
import com.waz.zclient.markdown.spans.custom.ListPrefixSpan
import com.waz.zclient.markdown.spans.custom.ParagraphSpacingSpan
import com.waz.zclient.markdown.visitors.SpanRenderer
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class SpanRendererTest {

    val parser = Parser.builder().build()
    val stylesheet = StyleSheet()

    fun sut(): SpanRenderer = SpanRenderer(stylesheet)
    fun parse(input: String): Node = parser.parse(input)

    /**
     * Returns array of strings for which the given kind of GroupSpan is present on the string.
     */
    private fun <T> SpannableString.spanResults(kind: Class<T>): List<String> {
        val spans = getSpans(0, length, kind)
        return spans.map { subSequence(getSpanStart(it), getSpanEnd(it)).toString() }
    }

    /**
     * Returns a 2D list of spans that are grouped by the given type. Each element in the outer
     * list represents the group span, each inner array is the component spans in that group.
     */
    private fun <T : GroupSpan> SpannableString.spansGroupedBy(kind: Class<T>): List<List<Any>> {
        val spans = getSpans(0, length, kind)
        return spans.map { it.spans }
    }

    /**
     * Returns spans of the given type whose start and end exactly match the given boundaries.
     */
    private fun <T> SpannableString.spansInRange(range: IntRange, kind: Class<T>): List<T> {
        val spans = getSpans(0, length, kind)
        return spans.filter { span -> getSpanStart(span) == range.start && getSpanEnd(span) == range.endInclusive }
    }

    /**
     * Compares the two lists and makes a comparison on each element after sorting.
     */
    fun check(results: List<String>, vararg elements: String) {
        val actual = results.sorted()
        val expected = elements.sorted()
        assertEquals(expected.size, actual.size)

        // assert on each element to easier debugging
        for (i in 0 until actual.size) assertEquals(expected[i], actual[i])
    }

    // -------------------------------------------------------------------------

    /**
     * These tests typically need to check two things:
     *
     *  1. The plain text string is the same as the input string without markdown syntax
     *  2. The correct GroupSpans are applied to the correct ranges in the spannable string.
     *
     */

    // SIMPLE
    // ----------------------------------------------------------------------------------

    @Test
    fun testThatItRenders_Simple_Inline_Emphasis() {
        // given
        val sut = sut()
        val input = "At **high noon** the *old* __man__ emerged _from the waves_"
        val expected = "At high noon the old man emerged from the waves"

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val emphasisResults = result.spanResults(EmphasisSpan::class.java)
        check(emphasisResults, "old", "from the waves")

        val strongEmphasisResults = result.spanResults(StrongEmphasisSpan::class.java)
        check(strongEmphasisResults, "high noon", "man")
    }

    @Test
    fun testThatItRenders_Simple_Inline_Code() {
        // given
        val sut = sut()
        val input = "and `found his` <em>fat-fed</em> seals and `made` his <p>rounds</p>"
        val expected = "and found his <em>fat-fed</em> seals and made his <p>rounds</p>"

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val codeResults = result.spanResults(CodeSpan::class.java)
        check(codeResults, "found his", "made")

        val htmlResults = result.spanResults(HtmlInlineSpan::class.java)
        check(htmlResults, "<em>", "</em>", "<p>", "</p>")
    }

    @Test
    fun testThatItRenders_Simple_Inline_Text() {
        // given
        val sut = sut()
        val input = "counting **them off**, counting us **the first four**"
        val expected = "counting them off, counting us the first four"

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val textResults = result.spanResults(TextSpan::class.java)
        check(textResults, "counting ", "them off", ", counting us ", "the first four")

        val strongEmphasisResults = result.spanResults(StrongEmphasisSpan::class.java)
        check(strongEmphasisResults, "them off", "the first four")
    }

    @Test
    fun testThatItRenders_Simple_Inline_Link() {
        // given
        val sut = sut()
        val input = "but he [had](wire.com) no inkling of [all the](wire.com) fraud afoot."
        val expected = "but he had no inkling of all the fraud afoot."

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val linkResults = result.spanResults(LinkSpan::class.java)
        check(linkResults, "had", "all the")
    }

    // NOTE: Images are not yet supported, they are currently identical to Links
    @Test
    fun testThatItRenders_Simple_Inline_Image() {
        // given
        val sut = sut()
        val input = "but he ![had](wire.com) no inkling of ![all the](wire.com) fraud afoot."
        val expected = "but he had no inkling of all the fraud afoot."

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val linkResults = result.spanResults(ImageSpan::class.java)
        check(linkResults, "had", "all the")
    }

    @Test
    fun testThatItRenders_Simple_Block_Heading() {
        // given
        val sut = sut()

        val input =
            """
                    |# H1
                    |body
                    |## H2
                    |body
                    |### H3
                    |body
                    |#### H4
                    |body
                    |#### H5
                    |body
                    |#### H6
                    |body
                    """.trimMargin()

        val expected =
            """
                    |H1
                    |body
                    |H2
                    |body
                    |H3
                    |body
                    |H4
                    |body
                    |H5
                    |body
                    |H6
                    |body
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val headingResults = result.spanResults(HeadingSpan::class.java)
        check(headingResults, "H1\n", "H2\n", "H3\n", "H4\n", "H5\n", "H6\n")
    }

    @Test
    fun testThatItRenders_Simple_Block_Paragraph() {
        // given
        val sut = sut()

        /**
         * - Paragraphs are separated by double linebreaks.
         * - The content of list items will also be inside a paragraph node.
         */
        val input =
            """
                    |At high noon the old man emerged from the waves and found his fat-fed
                    |seals and made his rounds, counting them off, counting us the first four.
                    |
                    |Then down he lay and slept, but we with a battle-cry, we rushed him,
                    |flung our arms around him.
                    |
                    |1. he'd lost nothing, the old rascal,
                    |2. none of his cunning quick techniques!
                    |
                    |First he shifted into a great bearded lion, then a serpent, a panther,
                    |a ramping wild boar, a torrent of water...
                    """.trimMargin()

        /**
         * - The rendered output will strip the extra linebreak between paragraphs
         * - List item content is separated by the prefix with a tab.
         */
        val expected =
            """
                    |At high noon the old man emerged from the waves and found his fat-fed
                    |seals and made his rounds, counting them off, counting us the first four.
                    |Then down he lay and slept, but we with a battle-cry, we rushed him,
                    |flung our arms around him.
                    |1.${"\t"}he'd lost nothing, the old rascal,
                    |2.${"\t"}none of his cunning quick techniques!
                    |First he shifted into a great bearded lion, then a serpent, a panther,
                    |a ramping wild boar, a torrent of water...
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val paragraphResults = result.spanResults(ParagraphSpan::class.java)

        /**
         * - Outer paragraphs must always span over the first and last characters of a paragraph,
         *   i.e they must include a trailing new line.
         * - Nested paragraphs (e.g. in list items) have no visual affect and the span merely
         *   serves as an identifier for the markdown unit. In this case, it should only span
         *   the list content (no trailing newline).
         */
        check(paragraphResults,
            """
                    |At high noon the old man emerged from the waves and found his fat-fed
                    |seals and made his rounds, counting them off, counting us the first four.
                    |
                    """.trimMargin(),

            """
                    |Then down he lay and slept, but we with a battle-cry, we rushed him,
                    |flung our arms around him.
                    |
                    """.trimMargin(),

            """
                    |he'd lost nothing, the old rascal,
                    """.trimMargin(),

            """
                    |none of his cunning quick techniques!
                    """.trimMargin(),

            """
                    |First he shifted into a great bearded lion, then a serpent, a panther,
                    |a ramping wild boar, a torrent of water...
                    """.trimMargin()
        )
    }

    @Test
    fun testThatItRenders_Simple_Block_Quote() {
        // given
        val sut = sut()

        val input =
            """
                    |> we held on for dear life,
                    |> braving it out until, at last,
                    |> that quick-change artist, the old wizard...
                    """.trimMargin()

        val expected =
            """
                    |we held on for dear life,
                    |braving it out until, at last,
                    |that quick-change artist, the old wizard...
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val quoteResults = result.spanResults(BlockQuoteSpan::class.java)
        check(quoteResults, expected)
    }

    @Test
    fun testThatItRenders_Simple_Block_QuoteWithSoftBreak() {
        // given
        val sut = sut()

        val input =
            """
                    |> we held on for dear life,
                    |braving it out until, at last,
                    |that quick-change artist, the old wizard...""".trimMargin()

        val expected =
            """
                    |we held on for dear life,
                    |braving it out until, at last,
                    |that quick-change artist, the old wizard...""".trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val quoteResults = result.spanResults(BlockQuoteSpan::class.java)
        check(quoteResults, expected)
    }

    @Test
    fun testThatItRenders_Simple_Block_OrderedList() {
        // given
        val sut = sut()

        val input =
            """
                    |3. we held on for dear life,
                    |1. braving it out until, at last,
                    |49. that quick-change artist, the old wizard...""".trimMargin()

        val expected =
            """
                    |3.${"\t"}we held on for dear life,
                    |4.${"\t"}braving it out until, at last,
                    |5.${"\t"}that quick-change artist, the old wizard...""".trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val listResults = result.spanResults(OrderedListSpan::class.java)
        check(listResults, expected)

        val itemResults = result.spanResults(ListItemSpan::class.java)

        // the span must include a trailing newline, except for the last.
        check(itemResults, "3.\twe held on for dear life,\n",
            "4.\tbraving it out until, at last,\n",
            "5.\tthat quick-change artist, the old wizard..."
        )

        val prefixResults = result.spanResults(ListPrefixSpan::class.java)
        check(prefixResults, "3.", "4.", "5.")
    }

    @Test
    fun testThatItRenders_Simple_Block_BulletList() {
        // given
        val sut = sut()

        val input =
            """
                    |* we held on for dear life,
                    |* braving it out until, at last,
                    |* that quick-change artist, the old wizard...""".trimMargin()

        val expected =
            """
                    |${"•\t"}we held on for dear life,
                    |${"•\t"}braving it out until, at last,
                    |${"•\t"}that quick-change artist, the old wizard...""".trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val listResults = result.spanResults(BulletListSpan::class.java)
        check(listResults, expected)

        val itemResults = result.spanResults(ListItemSpan::class.java)

        // the span must include a trailing newline, except for the last.
        check(itemResults,
            "•\twe held on for dear life,\n",
            "•\tbraving it out until, at last,\n",
            "•\tthat quick-change artist, the old wizard..."
        )

        val prefixResults = result.spanResults(ListPrefixSpan::class.java)
        check(prefixResults, "•", "•", "•")
    }

    @Test
    fun testThatItRenders_Simple_Block_FencedCode() {
        // given
        val sut = sut()

        val input =
            """
                    |```
                    |int meaningOfLife() {
                    |   return 42;
                    |}
                    |```""".trimMargin()

        val expected =
            """
                    |int meaningOfLife() {
                    |   return 42;
                    |}
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val codeResults = result.spanResults(FencedCodeBlockSpan::class.java)
        check(codeResults, expected)
    }

    @Test
    fun testThatItRenders_Simple_Block_IndentedCode() {
        // given
        val sut = sut()

        val input =
            """
                    |    int meaningOfLife() {
                    |        return 42;
                    |    }""".trimMargin()

        val expected =
            """
                    |int meaningOfLife() {
                    |    return 42;
                    |}
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val codeResults = result.spanResults(IndentedCodeBlockSpan::class.java)
        check(codeResults, expected)
    }

    @Test
    fun testThatItRenders_Simple_Block_Html() {
        // given
        val sut = sut()

        val input =
            """
                    |<html>
                    |   <body>So he urged, and broke the heart inside me.</body>
                    |</html>""".trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(input, result.toString())

        val codeResults = result.spanResults(HtmlBlockSpan::class.java)
        check(codeResults, input)
    }

    @Test
    fun testThatItRenders_Simple_Block_SoftLineBreak() {
        // given
        val sut = sut()

        val input =
            """
                    |But tell me this as well, and leave out nothing:
                    |Did all the Achaeans reach home in the ships unharmed,
                    |all we left behind, Nestor and I, en route from Troy?""".trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(input, result.toString())

        val lineBreakResults = result.spanResults(SoftLineBreakSpan::class.java)
        check(lineBreakResults, "\n", "\n")
    }

    @Test
    fun testThatItRenders_Simple_Block_HardLineBreak() {
        // given
        val sut = sut()

        val input =
            """
                    |But tell me this as well, and leave out nothing:\
                    |Did all the Achaeans reach home in the ships unharmed,\
                    |all we left behind, Nestor and I, en route from Troy?""".trimMargin()

        val expected =
            """
                    |But tell me this as well, and leave out nothing:
                    |Did all the Achaeans reach home in the ships unharmed,
                    |all we left behind, Nestor and I, en route from Troy?""".trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val lineBreakResults = result.spanResults(HardLineBreakSpan::class.java)
        check(lineBreakResults, "\n", "\n")
    }

    @Test
    fun testThatItRenders_Simple_Block_ThematicBreak() {
        // given
        val sut = sut()

        // there must be a break between the line and the text above, otherwise it renders
        // as a heading.
        val input =
            """
                    |Which god, Menelaus, conspired with you to trap me in ambush?
                    |
                    |----------------------------------
                    |Sieze me against my will? What on earth do you want?""".trimMargin()

        val expected =
            """
                    |Which god, Menelaus, conspired with you to trap me in ambush?
                    |---
                    |Sieze me against my will? What on earth do you want?""".trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val thematicBreakResults = result.spanResults(ThematicBreakSpan::class.java)
        check(thematicBreakResults, "---\n")
    }

    // COMPLEX
    // ----------------------------------------------------------------------------------

    @Test
    fun testThatItRenders_Complex_Heading() {
        // given
        val sut = sut()

        val input =
            """
                    |# **The Bewitching Queen of _Aeaea_**
                    |We reached the Aeolian island next, the home of Aeolus, Hippotas' son...
                    |## *The Cattle* of the Sun
                    |Now when our ship had left the Ocean River rolling in her wake...
                    |### `Ithaca at Last`
                    |His tale was now over. The Phaeacians all fell silent, hushed...
                    """.trimMargin()

        val expected =
            """
                    |The Bewitching Queen of Aeaea
                    |We reached the Aeolian island next, the home of Aeolus, Hippotas' son...
                    |The Cattle of the Sun
                    |Now when our ship had left the Ocean River rolling in her wake...
                    |Ithaca at Last
                    |His tale was now over. The Phaeacians all fell silent, hushed...
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val headingResults = result.spanResults(HeadingSpan::class.java)
        check(headingResults,
            "The Bewitching Queen of Aeaea\n",
            "The Cattle of the Sun\n",
            "Ithaca at Last\n"
        )

        val emphasisResults = result.spanResults(EmphasisSpan::class.java)
        check(emphasisResults, "Aeaea", "The Cattle")

        val strongEmphasisResults = result.spanResults(StrongEmphasisSpan::class.java)
        check(strongEmphasisResults, "The Bewitching Queen of Aeaea")

        val codeResults = result.spanResults(CodeSpan::class.java)
        check(codeResults, "Ithaca at Last")
    }

    @Test
    fun testThatItRenders_Complex_Emphasis() {
        // given
        val sut = sut()

        val input =
            """
                    |Then down he **lay _and_** *slept, __but__* we with a battle-cry, we rushed him,
                    |flung our arms **around him** -- he'd lost nothing, *the old rascal*,
                    |none of his **cunning _quick_ techniques!** First he *__shifted into__ a great* bearded lion
                    |and _then a **serpent**_, __a *panther*, a ramping *wild boar*, a *torrent*__ of water,
                    |a *__tree__ with __soaring__ branchtops...*
                    """.trimMargin()

        val expected =
            """
                    |Then down he lay and slept, but we with a battle-cry, we rushed him,
                    |flung our arms around him -- he'd lost nothing, the old rascal,
                    |none of his cunning quick techniques! First he shifted into a great bearded lion
                    |and then a serpent, a panther, a ramping wild boar, a torrent of water,
                    |a tree with soaring branchtops...
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val emphasisResults = result.spanResults(EmphasisSpan::class.java)
        check(emphasisResults,
            "and",
            "slept, but",
            "the old rascal",
            "quick",
            "shifted into a great",
            "then a serpent",
            "panther",
            "wild boar",
            "torrent",
            "tree with soaring branchtops..."
        )

        val strongEmphasisResults = result.spanResults(StrongEmphasisSpan::class.java)
        check(strongEmphasisResults,
            "lay and",
            "but",
            "around him",
            "cunning quick techniques!",
            "shifted into",
            "serpent",
            "a panther, a ramping wild boar, a torrent",
            "tree",
            "soaring"
        )
    }

    @Test
    fun testThatItRenders_Complex_List() {
        // given
        val sut = sut()

        val input =
            """
                    |1. There is an **island, _Ogygia_**, lying far at sea,
                    |2. where the daughter of *Atlas*, __`Calypso`__, has her `home`,
                    |3. the *`nymph`* with __*lovely braids*__ -- a **danger** too,
                    |4. and __*no one*, *god* or *mortal*__, _**`dares` approach her**_ there.
                    """.trimMargin()

        val expected =
            """
                    |1.${"\t"}There is an island, Ogygia, lying far at sea,
                    |2.${"\t"}where the daughter of Atlas, Calypso, has her home,
                    |3.${"\t"}the nymph with lovely braids -- a danger too,
                    |4.${"\t"}and no one, god or mortal, dares approach her there.
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val listResults = result.spanResults(OrderedListSpan::class.java)
        check(listResults, expected)

        val itemResults = result.spanResults(ListItemSpan::class.java)
        check(itemResults,
            "1.\tThere is an island, Ogygia, lying far at sea,\n",
            "2.\twhere the daughter of Atlas, Calypso, has her home,\n",
            "3.\tthe nymph with lovely braids -- a danger too,\n",
            "4.\tand no one, god or mortal, dares approach her there.")

        val emphasisResults = result.spanResults(EmphasisSpan::class.java)
        check(emphasisResults,
            "Ogygia",
            "Atlas",
            "nymph",
            "lovely braids",
            "no one",
            "god",
            "mortal",
            "dares approach her"
        )

        val strongEmphasisResults = result.spanResults(StrongEmphasisSpan::class.java)
        check(strongEmphasisResults,
            "island, Ogygia",
            "Calypso",
            "lovely braids",
            "danger",
            "no one, god or mortal",
            "dares approach her"
        )

        val codeResults = result.spanResults(CodeSpan::class.java)
        check(codeResults,
            "Calypso",
            "home",
            "nymph",
            "dares"
        )
    }

    @Test
    fun testThatItRenders_Complex_Quote() {
        // given
        val sut = sut()

        val input =
            """
                    |> There is an **island, _Ogygia_**, lying far at sea,
                    |> where the daughter of *Atlas*, __`Calypso`__, has her `home`,
                    |> the *`nymph`* with __*lovely braids*__ -- a **danger** too,
                    |> and __*no one*, *god* or *mortal*__, _**`dares` approach her**_ there.
                    """.trimMargin()

        val expected =
            """
                    |There is an island, Ogygia, lying far at sea,
                    |where the daughter of Atlas, Calypso, has her home,
                    |the nymph with lovely braids -- a danger too,
                    |and no one, god or mortal, dares approach her there.
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val listResults = result.spanResults(BlockQuoteSpan::class.java)
        check(listResults, expected)

        val emphasisResults = result.spanResults(EmphasisSpan::class.java)
        check(emphasisResults,
            "Ogygia",
            "Atlas",
            "nymph",
            "lovely braids",
            "no one",
            "god",
            "mortal",
            "dares approach her"
        )

        val strongEmphasisResults = result.spanResults(StrongEmphasisSpan::class.java)
        check(strongEmphasisResults,
            "island, Ogygia",
            "Calypso",
            "lovely braids",
            "danger",
            "no one, god or mortal",
            "dares approach her"
        )

        val codeResults = result.spanResults(CodeSpan::class.java)
        check(codeResults,
            "Calypso",
            "home",
            "nymph",
            "dares"
        )
    }

    @Test
    fun testThatItRenders_Complex_NestedList_NumbersFirst() {
        // given
        val sut = sut()

        val input =
            """
                    |1. Outer one
                    |2. Outer two
                    |   - Inner one
                    |   - Inner two
                    |3. Outer three
                    |""".trimMargin()

        val expected =
            """
                    |1.${"\t"}Outer one
                    |2.${"\t"}Outer two
                    |•${"\t"}Inner one
                    |•${"\t"}Inner two
                    |3.${"\t"}Outer three
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        // there is an ordered list span
        val orderedListResults = result.spanResults(OrderedListSpan::class.java)
        check(orderedListResults, expected)

        // there is a (nested) bullet list span (and it includes a leading newline)
        val bulletListResults = result.spanResults(BulletListSpan::class.java)
        check(bulletListResults,
            """
                    |
                    |•${"\t"}Inner one
                    |•${"\t"}Inner two
                    |
                    """.trimMargin()
        )

        // there is an item span for each item (note: 2nd item contains the nested list)
        val itemResults = result.spanResults(ListItemSpan::class.java)
        check(itemResults,
            "1.\tOuter one\n",
            """
                    |2.${"\t"}Outer two
                    |•${"\t"}Inner one
                    |•${"\t"}Inner two
                    |
                    """.trimMargin(),

            "•\tInner one\n",
            "•\tInner two\n",
            "3.\tOuter three"
        )

        /* The indentation spans are applied to each paragraph in a list item. Here each item
         * only has one paragraph each, but we still need to check them separately.
         */

        val rangesOfOuterItemLines = listOf(0..13, 13..26, 50..64)
        val rangesOfInnerItems = listOf(26..38, 38..50)

        for (range in rangesOfOuterItemLines) {
            val marginSpans = result.spansInRange(range, LeadingMarginSpan.Standard::class.java)
            assertEquals(1, marginSpans.size)
            assertEquals(0, marginSpans.first().getLeadingMargin(true))
            assertEquals(stylesheet.listItemContentMargin, marginSpans.first().getLeadingMargin(false))

            val tabStopSpans = result.spansInRange(range, TabStopSpan.Standard::class.java)
            assertEquals(1, tabStopSpans.size)
            assertEquals(stylesheet.listItemContentMargin, tabStopSpans.first().tabStop)
        }

        // the nested list has addition indentation
        val indentation = stylesheet.listItemContentMargin

        for (range in rangesOfInnerItems) {
            val marginSpans = result.spansInRange(range, LeadingMarginSpan.Standard::class.java)
            assertEquals(1, marginSpans.size)
            assertEquals(indentation, marginSpans.first().getLeadingMargin(true))
            assertEquals(indentation + stylesheet.listItemContentMargin, marginSpans.first().getLeadingMargin(false))

            val tabStopSpans = result.spansInRange(range, TabStopSpan.Standard::class.java)
            assertEquals(1, tabStopSpans.size)

            // the tabStop doesn't have the additional indentation because the leading margin
            // is already indented
            assertEquals( stylesheet.listItemContentMargin, tabStopSpans.first().tabStop)
        }

        // check that each prefix has a span
        val prefixResults = result.spanResults(ListPrefixSpan::class.java)
        check(prefixResults, "1.", "2.", "3.", "•", "•")

        // lastly, check for paragraph spacing spans on each item. These ranges represent
        // each item (excluding nested lists from items)
        for (range in listOf(0..13, 13..26, 26..38, 38..50, 50..64)) {
            val spacingSpans = result.spansInRange(range, ParagraphSpacingSpan::class.java)
            assertEquals(1, spacingSpans.size)
            assertEquals(stylesheet.listItemSpacingBefore, spacingSpans.first().before)
            assertEquals(stylesheet.listItemSpacingAfter, spacingSpans.first().after)
        }
    }

    @Test
    fun testThatItRenders_Complex_NestedList_BulletsFirst() {
        // given
        val sut = sut()

        val input =
            """
                    |- Outer one
                    |- Outer two
                    |   1. Inner one
                    |   2. Inner two
                    |- Outer three
                    |""".trimMargin()

        val expected =
            """
                    |•${"\t"}Outer one
                    |•${"\t"}Outer two
                    |1.${"\t"}Inner one
                    |2.${"\t"}Inner two
                    |•${"\t"}Outer three
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        // there is an ordered list span
        val bulletListResults = result.spanResults(BulletListSpan::class.java)
        check(bulletListResults, expected)

        // there is a (nested) bullet list span (and it includes a leading newline)
        val orderedListResults = result.spanResults(OrderedListSpan::class.java)
        check(orderedListResults,
            """
                    |
                    |1.${"\t"}Inner one
                    |2.${"\t"}Inner two
                    |
                    """.trimMargin()
        )

        // there is an item span for each item (note: 2nd item contains the nested list)
        val itemResults = result.spanResults(ListItemSpan::class.java)
        check(itemResults,
            "•\tOuter one\n",
            """
                    |•${"\t"}Outer two
                    |1.${"\t"}Inner one
                    |2.${"\t"}Inner two
                    |
                    """.trimMargin(),

            "1.\tInner one\n",
            "2.\tInner two\n",
            "•\tOuter three"
        )

        /* The indentation spans are applied to each paragraph in a list item. Here each item
         * only has one paragraph each, but we still need to check them separately.
         */

        val rangesOfOuterItemLines = listOf(0..12, 12..24, 50..63)
        val rangesOfInnerItems = listOf(24..37, 37..50)

        for (range in rangesOfOuterItemLines) {
            val marginSpans = result.spansInRange(range, LeadingMarginSpan.Standard::class.java)
            assertEquals(1, marginSpans.size)
            assertEquals(0, marginSpans.first().getLeadingMargin(true))
            assertEquals(stylesheet.listItemContentMargin, marginSpans.first().getLeadingMargin(false))

            val tabStopSpans = result.spansInRange(range, TabStopSpan.Standard::class.java)
            assertEquals(1, tabStopSpans.size)
            assertEquals(stylesheet.listItemContentMargin, tabStopSpans.first().tabStop)
        }

        // the nested list has addition indentation
        val indentation = stylesheet.listItemContentMargin

        for (range in rangesOfInnerItems) {
            val marginSpans = result.spansInRange(range, LeadingMarginSpan.Standard::class.java)
            assertEquals(1, marginSpans.size)
            assertEquals(indentation, marginSpans.first().getLeadingMargin(true))
            assertEquals(indentation + stylesheet.listItemContentMargin, marginSpans.first().getLeadingMargin(false))

            val tabStopSpans = result.spansInRange(range, TabStopSpan.Standard::class.java)
            assertEquals(1, tabStopSpans.size)

            // the tabStop doesn't have the additional indentation because the leading margin
            // is already indented
            assertEquals( stylesheet.listItemContentMargin, tabStopSpans.first().tabStop)
        }

        // check that each prefix has a span
        val prefixResults = result.spanResults(ListPrefixSpan::class.java)
        check(prefixResults, "1.", "2.", "•", "•", "•")

        // lastly, check for paragraph spacing spans on each item. These ranges represent
        // each item (excluding nested lists from items)
        for (range in listOf(0..12, 12..24, 24..37, 37..50, 50..63)) {
            val spacingSpans = result.spansInRange(range, ParagraphSpacingSpan::class.java)
            assertEquals(1, spacingSpans.size)
            assertEquals(stylesheet.listItemSpacingBefore, spacingSpans.first().before)
            assertEquals(stylesheet.listItemSpacingAfter, spacingSpans.first().after)
        }
    }

    @Test
    fun testThatItRenders_Complex_NestedList_Triple() {
        // given
        val sut = sut()

        val input =
            """
                    |1. Outer
                    |    - Inner
                    |       1. Inner Inner
                    |""".trimMargin()

        val expected =
            """
                    |1.${"\t"}Outer
                    |•${"\t"}Inner
                    |1.${"\t"}Inner Inner
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        // there are two ordered list spans
        val orderedListResults = result.spanResults(OrderedListSpan::class.java)
        check(orderedListResults, expected, "\n1.\tInner Inner")

        // there is one bullet list span (and it includes a leading newline)
        val bulletListResults = result.spanResults(BulletListSpan::class.java)
        check(bulletListResults, "\n•\tInner\n1.\tInner Inner")

        // there is an item span for each item (note: nested lists are within items)
        val itemResults = result.spanResults(ListItemSpan::class.java)
        check(itemResults, expected, "•\tInner\n1.\tInner Inner", "1.\tInner Inner")

        // now check the indentation of each item
        val rangesOfItems = listOf(0..9, 9..17, 17..31)
        val indentation = stylesheet.listItemContentMargin

        for ((idx, range) in rangesOfItems.withIndex()) {
            val marginSpans = result.spansInRange(range, LeadingMarginSpan.Standard::class.java)
            assertEquals(1, marginSpans.size)

            // the depth of the list item increases its indentation
            assertEquals(idx * indentation, marginSpans.first().getLeadingMargin(true))
            assertEquals((idx + 1) * indentation, marginSpans.first().getLeadingMargin(false))

            val tabStopSpans = result.spansInRange(range, TabStopSpan.Standard::class.java)
            assertEquals(1, tabStopSpans.size)

            // but the depth of the list item doesn't affect its tabstop (because the
            // leading margins are already indented)
            assertEquals(stylesheet.listItemContentMargin, tabStopSpans.first().tabStop)
        }

        // finally check the prefixes
        val prefixResults = result.spanResults(ListPrefixSpan::class.java)
        check(prefixResults, "1.", "•", "1.")
    }

    @Test
    fun testThatItRenders_Complex_ListWithSoftLineBreaks() {
        /*
         * Text after a soft break in a list item is still considered part of the item,
         * so it should be indented to that item. For example:
         *
         * 1. Odyssesus                    1. Odysseus
         * 2. Penelope,            ->      2. Penelope,
         * wife of Odysseus                   wife of Odysseus
         * 3. Telemachus                   3. Telemachus
         */

        // given
        val sut = sut()

        val input =
            """
                    |1. Odysseus
                    |2. Penelope,
                    |wife of Odysseus
                    |3. Telemachus
                    """.trimMargin()

        val expected =
            """
                    |1.${"\t"}Odysseus
                    |2.${"\t"}Penelope,
                    |wife of Odysseus
                    |3.${"\t"}Telemachus
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val listResults = result.spanResults(OrderedListSpan::class.java)
        check(listResults, expected)

        /* There are two leading margin spans to check, one for the first paragraph of the list
         * item, and one for the rest of the paragraphs up until the end of the item.
         */

        // leading margin spans on the whole second item
        var spans = result.spansInRange(12..25, LeadingMarginSpan::class.java)
        assertEquals(1, spans.size)

        // it should have 0 indentation for the first line, but indentation for the rest
        assertEquals(0, spans.first().getLeadingMargin(true))
        assertEquals(stylesheet.listItemContentMargin, spans.first().getLeadingMargin(false))

        // leading margin spans from the linebreak to the end of the item
        spans = result.spansInRange(25..42, LeadingMarginSpan::class.java)
        assertEquals(1, spans.size)

        // it should have indentation for the first line, but 0 for the rest
        assertEquals(stylesheet.listItemContentMargin, spans.first().getLeadingMargin(true))
        assertEquals(stylesheet.listItemContentMargin, spans.first().getLeadingMargin(false))
    }

    @Test
    fun testThatItRenders_Complex_NestedListWithSoftLineBreaks() {
        /*
         * If an item contains a break in its content, which is followed by a nested list,
         * then we need to ensure that only the content between the break and the nested
         * list is correctly indented in the list item. For example:

         * 1. Odyssesus                            1. Odysseus
         * 2. Penelope,                    ->      2. Penelope,
         * wife of Odysseus                           wife of Odysseus
         *     - mother of Telemachus                 • mother of Telemachus
         *     - daughter of Icarius                  • daughter of Icarius
         * 3. Telemachus                           3. Telemachus
         */

        // given
        val sut = sut()

        val input =
            """
                    |1. Odysseus
                    |2. Penelope,
                    |wife of Odysseus
                    |   - mother of Telemachus
                    |   - daughter of Icarius
                    |3. Telmachus
                    """.trimMargin()

        val expected =
            """
                    |1.${"\t"}Odysseus
                    |2.${"\t"}Penelope,
                    |wife of Odysseus
                    |•${"\t"}mother of Telemachus
                    |•${"\t"}daughter of Icarius
                    |3.${"\t"}Telmachus
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        val orderedListResults = result.spanResults(OrderedListSpan::class.java)
        check(orderedListResults, expected)

        val bulletListResults = result.spanResults(BulletListSpan::class.java)
        check(bulletListResults,
            """
                    |
                    |•${"\t"}mother of Telemachus
                    |•${"\t"}daughter of Icarius
                    |
                    """.trimMargin()
        )

        /* There are two leading margins spans to check, one for the first paragraph of the item,
         * and one for the rest of the paragraphs up to the nested list.
         */

        // leading margin span for the whole second list item
        var spans = result.spansInRange(12..25, LeadingMarginSpan::class.java)
        assertEquals(1, spans.size)

        // it should have 0 indentation for the first line, but indentation for the rest
        assertEquals(0, spans.first().getLeadingMargin(true))
        assertEquals(stylesheet.listItemContentMargin, spans.first().getLeadingMargin(false))

        // leading margin span from the soft line break, up to the start of the nested list
        spans = result.spansInRange(25..41, LeadingMarginSpan::class.java)
        assertEquals(1, spans.size)

        // it should have indentation for the all lines
        assertEquals(stylesheet.listItemContentMargin, spans.first().getLeadingMargin(true))
        assertEquals(stylesheet.listItemContentMargin, spans.first().getLeadingMargin(false))

        // lastly, check for paragraph spacing spans on each item. These ranges represent
        // each item (excluding nested lists from items)
        for (range in listOf(0..12, 12..41, 42..65, 65..87, 87..99)) {
            val spacingSpans = result.spansInRange(range, ParagraphSpacingSpan::class.java)
            assertEquals(1, spacingSpans.size)
            assertEquals(stylesheet.listItemSpacingBefore, spacingSpans.first().before)
            assertEquals(stylesheet.listItemSpacingAfter, spacingSpans.first().after)
        }
    }

    @Test
    fun testThatItRenders_Complex_NestedQuote() {
        // given
        val sut = sut()

        val input =
            """
                    |> Outer one
                    |> Outer two
                    |>> Inner one
                    |>> Inner two
                    |> Outer three
                    """.trimMargin()

        val expected =
            """
                    |Outer one
                    |Outer two
                    |Inner one
                    |Inner two
                    |Outer three
                    """.trimMargin()

        // when
        parse(input).accept(sut)
        val result = sut.spannableString

        // then
        assertEquals(expected, result.toString())

        // nested quotes not currently supported, so we only expect one flat quote.
        val quoteResults = result.spanResults(BlockQuoteSpan::class.java)
        check(quoteResults, expected)
    }
}
