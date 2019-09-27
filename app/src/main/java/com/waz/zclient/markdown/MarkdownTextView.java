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
import android.content.ContextWrapper;
import android.text.SpannableString;
import android.util.AttributeSet;

import com.waz.model.AccentColor;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.ViewHelper;
import com.waz.zclient.common.controllers.global.AccentColorCallback;
import com.waz.zclient.common.controllers.global.AccentColorController;
import com.waz.zclient.common.views.LinkTextView;
import com.waz.zclient.markdown.spans.GroupSpan;
import com.waz.zclient.markdown.spans.commonmark.ImageSpan;
import com.waz.zclient.markdown.spans.commonmark.LinkSpan;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.utils.ContextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarkdownTextView extends TypefaceTextView implements ViewHelper {
    public MarkdownTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MarkdownTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarkdownTextView(Context context) {
        super(context);
    }

    private StyleSheet mStyleSheet;

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        invalidateStyleSheet();
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        invalidateStyleSheet();
    }

    private void invalidateStyleSheet() {
        mStyleSheet = null;
    }

    /**
     * This function is taken from this Stackoverflow answer:
     * https://stackoverflow.com/questions/8276634/android-get-hosting-activity-from-a-view/32973351#32973351
     */
    private BaseActivity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof BaseActivity) {
                return (BaseActivity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        return null;
    }

    /**
     * Configures the style sheet used for rendering.
     */
    private void configureStyleSheet() {
        mStyleSheet = new StyleSheet();
        mStyleSheet.setBaseFontColor(getCurrentTextColor());
        mStyleSheet.setBaseFontSize((int) getTextSize());
        mStyleSheet.setCodeColor(ContextUtils.getStyledColor(R.attr.codeColor, context()));
        mStyleSheet.setQuoteColor(ContextUtils.getStyledColor(R.attr.quoteColor, context()));
        mStyleSheet.setQuoteStripeColor(ContextUtils.getStyledColor(R.attr.quoteStripeColor, context()));
        mStyleSheet.setListPrefixColor(ContextUtils.getStyledColor(R.attr.listPrefixColor, context()));

        BaseActivity activity = (BaseActivity) getContext();

        if (activity != null) {
            // update the link color whenever the accent color changes
            activity.injectJava(AccentColorController.class).accentColorForJava(new AccentColorCallback() {
                @Override
                public void color(AccentColor color) {
                    mStyleSheet.setLinkColor(color.color());
                    setLinkTextColor(color.color());
                    refreshLinks();
                }
            }, eventContext());
        }

        // to make links clickable
        mStyleSheet.configureLinkHandler(context());
        setMovementMethod(new LinkTextView.MovementMethod(getContext()));

        setLineSpacing(0f, 1.1f);
    }

    private void configureQuoteStyleSheet() {
        mStyleSheet = new StyleSheet();
        mStyleSheet.setBaseFontColor(getCurrentTextColor());
        mStyleSheet.setBaseFontSize((int) getTextSize());
        mStyleSheet.setCodeColor(getCurrentTextColor());
        mStyleSheet.setQuoteColor(getCurrentTextColor());
        mStyleSheet.setQuoteStripeColor(getCurrentTextColor());
        mStyleSheet.setListPrefixColor(getCurrentTextColor());
        mStyleSheet.setLinkColor(getCurrentTextColor());
        setLinkTextColor(getCurrentTextColor());
        setLineSpacing(0f, 1.1f);
    }

    /**
     * Mark down the text currently in the buffer.
     */
    public void markdown() {
        if (mStyleSheet == null) { configureStyleSheet(); }
        applyMarkdown();
    }

    public void markdownQuotes() {
        if (mStyleSheet == null) { configureQuoteStyleSheet(); }
        applyMarkdown();
    }

    private void applyMarkdown() {
        String text = getText().toString();
        SpannableString result = Markdown.parse(text, mStyleSheet);
        setText(result);
    }

    /**
     * Re-applies all LinkSpan and ImageSpan objects. Call this method after Linkifying the text
     * preserve existing markdown links, or after changing the link color in the stylesheet.
     */
    public void refreshLinks() {
        if (!(getText() instanceof SpannableString)) { return; }

        SpannableString text = (SpannableString) getText();
        GroupSpan[] linkSpans = text.getSpans(0, text.length(), LinkSpan.class);
        GroupSpan[] imageSpans = text.getSpans(0, text.length(), ImageSpan.class);
        List<GroupSpan> allSpans = new ArrayList<>(Arrays.asList(linkSpans));
        allSpans.addAll(Arrays.asList(imageSpans));

        for (GroupSpan span : allSpans) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            int flags = text.getSpanFlags(span);

            // remove the group span & its subspans
            text.removeSpan(span);
            for (Object subspan : span.getSpans()) { text.removeSpan(subspan); }

            // generate a new one (link color may have changed)
            GroupSpan newGroupSpan = mStyleSheet.spanFor(span.toNode(null));

            // add the group span & its subspans
            text.setSpan(newGroupSpan, start, end, flags);
            for (Object subspan : newGroupSpan.getSpans()) { text.setSpan(subspan, start, end, flags); }
        }
    }
}
