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
package com.waz.zclient.ui.text;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;

import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;
import com.waz.zclient.core.logging.Logger;
import com.waz.zclient.markdown.MarkdownTextView;

/**
 * This view will automatically linkify the text passed to {@link LinkTextView#setTextLink()}}, but will not steal
 * touch events that are not inside a URLSpan
 */
public class LinkTextView extends MarkdownTextView {

    @SuppressWarnings("unused")
    public LinkTextView(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public LinkTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("unused")
    public LinkTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.TypefaceTextView,
            0, 0);

        String font = a.getString(R.styleable.TypefaceTextView_w_font);
        if (!TextUtils.isEmpty(font) && !isInEditMode()) {
            setTypeface(font);
        }

        String transform = a.getString(R.styleable.TypefaceTextView_transform);
        if (!TextUtils.isEmpty(transform) && getText() != null) {
            setTransformedText(getText().toString(), transform);
        }

        a.recycle();

    }

    public void setTextLink() {
        // TODO: remove try/catch blocks when the bug is fixed
        try {
            if (Linkify.addLinks(this, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS)) {
                stripUnderlines();
            }
        } catch (Throwable t) {
            // ignore
        }

        // Linkify.addLinks() removes all existing URLSpan objects, so we need to re-apply
        // the ones added generated through markdown.
        try {
            refreshLinks();
        } catch(ArrayIndexOutOfBoundsException ex) {
            Logger.info("LinkTextView", "Error while refreshing links. text:" + getText());
            if (BuildConfig.FLAVOR.equals("internal")) {
                throw ex;
            }
        }
    }

    /*
     * This part (the method stripUnderlines) of the Wire software uses source coded posted on the StackOverflow site.
     * (http://stackoverflow.com/a/9852280/1751834)
     *
     * That work is licensed under a Creative Commons Attribution-ShareAlike 2.5 Generic License.
     * (http://creativecommons.org/licenses/by-sa/2.5)
     *
     * Contributors on StackOverflow:
     *  - Andrei (http://stackoverflow.com/users/570217)
     */
    private void stripUnderlines() {
        if (getText() == null) {
            return;
        }
        if (!(getText() instanceof Spannable)) {
            return;
        }
        Spannable s = (Spannable) getText();
        URLSpan[] spans = s.getSpans(0, s.length(), URLSpan.class);
        for (URLSpan span: spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);
            s.removeSpan(span);
            URLSpan spanNew = new URLSpanNoUnderline(span.getURL());
            s.setSpan(spanNew, start, end, 0);
        }
        setText(s);
    }

    private static class URLSpanNoUnderline extends URLSpan {

        public static final Parcelable.Creator<URLSpanNoUnderline> CREATOR
            = new Parcelable.Creator<URLSpanNoUnderline>() {
            public URLSpanNoUnderline createFromParcel(Parcel in) {
                return new URLSpanNoUnderline(in);
            }

            public URLSpanNoUnderline[] newArray(int size) {
                return new URLSpanNoUnderline[size];
            }
        };

        URLSpanNoUnderline(String url) {
            super(url);
        }

        URLSpanNoUnderline(Parcel in) {
            super(in);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }

}
