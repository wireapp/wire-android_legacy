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
import com.waz.zclient.markdown.visitors.SpanRenderer
import org.commonmark.parser.Parser

class Markdown {
    companion object {
        @JvmStatic
        fun parse(input: String, style: StyleSheet? = null): SpannableString {
            val document = Parser.builder().build().parse(input)
            val renderer = SpanRenderer(style ?: StyleSheet())
            document.accept(renderer)
            return renderer.spannableString
        }
    }
}
