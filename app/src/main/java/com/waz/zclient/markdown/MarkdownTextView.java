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
package com.waz.zclient.markdown;

import android.content.Context;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;

import com.waz.zclient.markdown.spans.GroupSpan;
import com.waz.zclient.markdown.spans.commonmark.ImageSpan;
import com.waz.zclient.markdown.spans.commonmark.LinkSpan;
import com.waz.zclient.ui.text.TypefaceTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarkdownTextView extends TypefaceTextView {
    public MarkdownTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MarkdownTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarkdownTextView(Context context) {
        super(context);
    }

    /**
     * Mark down the text currently in the buffer.
     */
    public void markdown() {
        StyleSheet ss = StyleSheet.Companion.styleFor(this);

        // to make links clickable
        setMovementMethod(LinkMovementMethod.getInstance());

        String text = getText().toString();
        SpannableString result = Markdown.parse(text, ss);
        setText(result);
    }

    /**
     * Re-applies all LinkSpan and ImageSpan objects.
     */
    public void refreshLinks() {
        if (!(getText() instanceof SpannableString)) { return; }

        SpannableString text = (SpannableString) getText();
        GroupSpan[] linkSpans = text.getSpans(0, text.length(), LinkSpan.class);
        GroupSpan[] imageSpans = text.getSpans(0, text.length(), ImageSpan.class);
        List<GroupSpan> allSpans = new ArrayList<>(Arrays.asList(linkSpans));
        allSpans.addAll(Arrays.asList(imageSpans));


        for (GroupSpan span: allSpans) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            int flags = text.getSpanFlags(span);

            // remove subspans & re-add them
            for (Object subspan: span.getSpans()) {
                text.removeSpan(subspan);
                text.setSpan(subspan, start, end, flags);
            }
        }
    }
}
