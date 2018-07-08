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
package com.waz.zclient.ui.utils;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.Pair;
import android.view.View;
import android.widget.TextView;
import com.waz.zclient.R;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.List;


public class TextViewUtils {

    private TextViewUtils() { }

    /**
     * Will highlight a single section of a TextView between 2 "_" marks.
     * For example, in "Click _HERE_ please." "HERE" will be made the color specified and bolded.
     * @param textView
     * @param highlightColor
     *
     * CAUTION: This does not work together with textAllCaps attribute!
     */
    public static void highlightAndBoldText(TextView textView, int highlightColor) {
        final String string = textView.getText().toString();
        textView.setText(getHighlightText(textView.getContext(), string, highlightColor, true));
    }

    public static CharSequence getHighlightText(Context context, String string, int highlightColor, boolean bold) {
        final int highlightStart = string.indexOf('_');

        if (highlightStart < 0) {
            Timber.e("Failed to highlight text - could not find _ marker in string.");
            return string;
        }

        final int highlightEnd = string.lastIndexOf('_');
        if (highlightStart >= highlightEnd) {
            Timber.e("Failed to highlight text - make sure you have 2 _ markers to denote start and end of highlight region");
            return string;
        }

        StringBuilder stringBuilder = new StringBuilder(string.substring(0, highlightStart));
        stringBuilder.append(string.substring(highlightStart + 1, highlightEnd));
        if (highlightEnd < string.length() - 1) {
            stringBuilder.append(string.substring(highlightEnd + 1, string.length()));
        }


        SpannableString colorSpannable = new SpannableString(stringBuilder.toString());
        colorSpannable.setSpan(new ForegroundColorSpan(highlightColor),
                               highlightStart,
                               highlightEnd - 1,
                               Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            colorSpannable.setSpan(new CustomTypefaceSpan("",
                                                          context.getResources().getString(R.string.wire__typeface__bold)),
                                   highlightStart,
                                   highlightEnd - 1,
                                   Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return colorSpannable;
    }


    /**
     * Will bold all sections of the text in a TextView which have [[ ]] around them.
     * For example "Here [[we]] are [[now]]." would bold "we" and "now".
     * @param textView
     *
     * CAUTION: This does not work together with textAllCaps attribute!
     */
    public static void boldText(TextView textView) {
        highlightText(textView, R.string.wire__typeface__bold);
    }

    public static CharSequence getBoldText(Context context, String text) {
        return getTypefaceHighlightText(context, text, R.string.wire__typeface__bold, null, 0, 0);
    }

    public static CharSequence getBoldHighlightText(Context context, String text, int highlightColor, int highlightStart, int highlightEnd) {
        return getTypefaceHighlightText(context, text, R.string.wire__typeface__bold, highlightColor, highlightStart, highlightEnd);
    }

    /**
     * Will make all sections of the text in a TextView which have [[ ]] around them medium font.
     * For example "Here [[we]] are [[now]]." would make "we" and "now" medium.
     * @param textView
     *
     * CAUTION: This does not work together with textAllCaps attribute!
     */
    public static void mediumText(TextView textView) {
        highlightText(textView, R.string.wire__typeface__regular);
    }

    /**
     * Will highlight all sections with the desired {@param typefaceRes} of the text in a TextView which have [[ ]] around them.
     * For example "Here [[we]] are [[now]]." would highlight "we" and "now".
     * @param textView
     *
     * CAUTION: This does not work together with textAllCaps attribute!
     */
    public static void highlightText(TextView textView, @StringRes int typefaceRes) {
        String string = textView.getText().toString();
        textView.setText(getTypefaceHighlightText(textView.getContext(), string, typefaceRes, null, 0, 0));
    }

    private static CharSequence getTypefaceHighlightText(Context context, String string, @StringRes int typefaceRes,
                                                         Integer highlightColor, int colorHighlightStart, int colorHighlightEnd) {
        List<Pair<Integer, Integer>> spanPositions = new ArrayList<>();
        int highlightStart;
        int highlightEnd = 0;

        while (string.substring(highlightEnd, string.length()).contains("[[")) {
            highlightStart = string.indexOf("[[");
            highlightEnd = string.indexOf("]]") - 2;
            spanPositions.add(new Pair<>(highlightStart, highlightEnd));
            string = string.replaceFirst("\\[\\[", "").replaceFirst("]]", "");
            if (highlightColor != null && colorHighlightStart <= highlightStart && colorHighlightEnd >= highlightEnd) {
                // need to deduct the [[ and ]] from the color span
                colorHighlightEnd -= 4;
            }
        }

        SpannableString highlightSpannable = new SpannableString(string);
        for (Pair<Integer, Integer> spanPosition : spanPositions) {
            highlightSpannable.setSpan(new CustomTypefaceSpan("", context.getResources().getString(typefaceRes)),
                                       spanPosition.first,
                                       spanPosition.second,
                                       Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (highlightColor != null) {
            highlightSpannable.setSpan(new ForegroundColorSpan(highlightColor),
                                       colorHighlightStart,
                                       colorHighlightEnd,
                                       Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return highlightSpannable;
    }

    public static void linkifyText(TextView textView, final int highlightColor, boolean bold, final Runnable onClick) {
        linkifyText(textView, highlightColor, bold, true, onClick);
    }

    /**
     * Will highlight a single section of a TextView between 2 "_" marks, and make it clickable.
     * Tapping the highlighted region will run the specified Runnable.
     * For example, in "Click _HERE_ please." "HERE" will be made the color specified and bolded.
     *
     * CAUTION: This does not work together with textAllCaps attribute!
     * @param textView
     * @param highlightColor
     * @param bold
     * @param underline
     * @param onClick
     */
    public static void linkifyText(TextView textView, final int highlightColor, boolean bold, final boolean underline, final Runnable onClick) {
        linkifyText(textView, highlightColor, bold ? R.string.wire__typeface__medium : -1, underline, onClick);
    }

    public static void linkifyText(TextView textView, final int highlightColor, int boldTypeface, final boolean underline, final Runnable onClick) {
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        final String string = textView.getText().toString();
        final int highlightStart = string.indexOf('_');

        if (highlightStart < 0) {
            Timber.e("Failed to highlight text - could not find _ marker in string.");
            return;
        }

        final int highlightEnd = string.lastIndexOf('_') - 1;
        if (highlightStart >= highlightEnd) {
            Timber.e("Failed to highlight text - make sure you have 2 _ markers to denote start and end of highlight region");
            return;
        }

        final SpannableStringBuilder str = new SpannableStringBuilder(textView.getText());
        str.replace(highlightStart, (highlightStart + 1), "");
        str.replace(highlightEnd, (highlightEnd + 1), "");

        final Typeface typeface = textView.getTypeface();
        ClickableSpan linkSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                if (onClick == null) {
                    return;
                }
                onClick.run();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setUnderlineText(underline);
                ds.setTypeface(typeface);
                ds.setColor(highlightColor);
            }
        };

        str.setSpan(linkSpan, highlightStart, highlightEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (boldTypeface > 0) {
            str.setSpan(new CustomTypefaceSpan("", textView.getResources().getString(boldTypeface)),
                                    highlightStart,
                                    highlightEnd,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        textView.setText(str);
    }

    public static class CustomTypefaceSpan extends TypefaceSpan {

        public static final Parcelable.Creator<CustomTypefaceSpan> CREATOR
            = new Parcelable.Creator<CustomTypefaceSpan>() {
            public CustomTypefaceSpan createFromParcel(Parcel in) {
                return new CustomTypefaceSpan(in);
            }

            public CustomTypefaceSpan[] newArray(int size) {
                return new CustomTypefaceSpan[size];
            }
        };

        private final String type;
        private final Typeface newType;

        public CustomTypefaceSpan(String family, String type) {
            super(family);
            this.type = type;
            this.newType = TypefaceUtils.getTypeface(type);
        }

        public CustomTypefaceSpan(Parcel in) {
            super(in);
            this.type = in.readString();
            this.newType = TypefaceUtils.getTypeface(type);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            applyCustomTypeFace(ds, newType);
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint paint) {
            applyCustomTypeFace(paint, newType);
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(type);
        }

        private static void applyCustomTypeFace(Paint paint, Typeface tf) {
            paint.setTypeface(tf);
        }
    }
}
