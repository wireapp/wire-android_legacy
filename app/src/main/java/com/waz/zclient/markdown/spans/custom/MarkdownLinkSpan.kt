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
package com.waz.zclient.markdown.spans.custom

import android.text.TextPaint
import android.text.style.URLSpan
import android.view.View
import android.webkit.URLUtil
import com.waz.zclient.BuildConfig

/**
 * MarkdownLinkSpan is a URLSpan without underline styling. Furthermore, an onClick handler
 * can be provided which is called with the url as the only argument.
 */
class MarkdownLinkSpan(url: String, val onClick: (String) -> Unit): URLSpan(url) {

    override fun onClick(widget: View) {
        if (url.startsWith(BuildConfig.CUSTOM_URL_SCHEME)) onClick(url)
        else onClick(URLUtil.guessUrl(url))
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
    }
}
