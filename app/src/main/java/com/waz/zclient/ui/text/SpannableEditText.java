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
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.waz.zclient.utils.ViewUtils;

import java.util.Arrays;
import java.util.Comparator;

public class SpannableEditText extends AccentColorEditText {

    private static final int TOKEN_CLICK_EVENT_DISTANCE_TOLERANCE_DP = 3;

    private Callback callback;

    // Save event coordinates on ACTION_DOWN
    private float gestureStartX;
    private float gestureStartY;
    // True if selection can be set on edit text
    private boolean canSetSelection = true;
    protected boolean notifyTextWatcher;

    public interface Callback {
        void onRemovedTokenSpan(String id);

        void onClick(View v);
    }

    public static abstract class TokenSpan extends ReplacementSpan {
        public abstract String getId();

        public abstract String getText();

        public abstract Boolean getDeleteMode();

        public abstract void setDeleteMode(boolean deleteMode);

        public abstract Boolean spanClicked(int clickPosition);
    }

    public SpannableEditText(Context context) {
        this(context, null);
    }

    public SpannableEditText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpannableEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) {
                    callback.onClick(v);
                }
            }
        });

        // Determine which span and if delete button inside span was clicked
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    gestureStartX = event.getX();
                    gestureStartY = event.getY();
                    canSetSelection = true;
                }

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    canSetSelection = false;
                }

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float eventDistance = (float) Math.sqrt((gestureStartX - event.getX()) * (gestureStartX - event.getX()) + (gestureStartY - event.getY()) * (gestureStartY - event.getY()));
                    float distanceTolerance = ViewUtils.toPx(getContext(), TOKEN_CLICK_EVENT_DISTANCE_TOLERANCE_DP);
                    if (eventDistance < distanceTolerance) {
                        clickedTokenSpan(event);
                    }
                }

                return false;
            }
        });
        notifyTextWatcher = true;
    }

    private void clickedTokenSpan(MotionEvent event) {
        TokenSpan span = getTouchedSpan(event);
        if (span == null) {
            resetDeleteModeForSpans();
            return;
        }
        int clickPosition = getEventXInsideSpan(event, span);

        // Clicked on a span (but not on delete button) -> toggle delete mode
        if (span.spanClicked(clickPosition)) {
            toggleSpanDeleteMode(span.getId());
        } else {
            resetDeleteModeForSpans();
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (!canSetSelection) {
            return;
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    private TokenSpan getTouchedSpan(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();

        Layout layout = getLayout();
        int line = layout.getLineForVertical(y + getScrollY());
        int offset = layout.getOffsetForHorizontal(line, x);

        Editable buffer = getText();
        TokenSpan[] spans = getSpans(buffer, offset, offset);
        if (spans.length == 0) {
            return null;
        }

        // Return first token at offset that is also at the same line as touch event
        for (TokenSpan span : spans) {
            int end = getText().getSpanEnd(span);
            if ((getPaddingLeft() + getLayout().getPrimaryHorizontal(end)) >= x) {
                return  span;
            }

        }
        return spans[spans.length - 1];
    }

    private int getEventXInsideSpan(MotionEvent event, TokenSpan span) {
        int start = getText().getSpanStart(span);
        return (int) (event.getX() - getLayout().getPrimaryHorizontal(start) - getPaddingLeft());
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    /**
     * True if EditText is empty (no spans and no text)
     */
    public boolean isEmpty() {
        if (getText() == null) {
            return true;
        }
        return TextUtils.isEmpty(getText().toString().trim());
    }

    /**
     * Returns non-spannable text in text view
     */
    public String getNonSpannableText() {
        Editable buffer = getText();
        String result = buffer.toString();
        TokenSpan[] spans = getSpans(buffer);
        for (int i = spans.length - 1; i >= 0; i--) {
            TokenSpan span = spans[i];
            int start = buffer.getSpanStart(span);
            int end = buffer.getSpanEnd(span);
            String before = result.substring(0, start);
            String after = result.substring(end);
            result = before + after;
        }
        return result;
    }

    /**
     * Clears non-spannable text in text view
     */
    public void clearNonSpannableText() {
        Editable buffer = getText();
        TokenSpan[] spans = getSpans(buffer);
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int start = 0;
        for (TokenSpan span : spans) {
            int end = start + span.getText().length();
            ssb.append(span.getText());
            ssb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = end;
        }
        setText(ssb);
        setSelection(getText().length());
    }

    /**
     * Appends a span to the end of text view
     */
    public void appendSpan(TokenSpan tokenSpan) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(getText());
        int start = getText().length();
        int end = start + tokenSpan.getText().length();
        ssb.append(tokenSpan.getText());
        ssb.setSpan(tokenSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        setText(ssb);
        setSelection(getText().length());
    }

    public boolean removeSpan(String id) {
        Editable buffer = getText();
        TokenSpan[] spans = getSpans(buffer);
        for (int i = spans.length - 1; i >= 0; i--) {
            TokenSpan span = spans[i];
            if (span.getId().equals(id)) {
                removeSpan(span);
                setSelection(getText().length());
                return true;
            }
        }
        return false;
    }
    /**
     * Removes a span
     */
    protected void removeSpan(TokenSpan span) {
        Editable buffer = getText();
        int start = buffer.getSpanStart(span);
        int end = buffer.getSpanEnd(span);
        buffer.removeSpan(span);
        buffer.replace(start, end, "");

        if (callback != null) {
            callback.onRemovedTokenSpan(span.getId());
        }
    }

    protected void resetDeleteModeForSpans() {
        Editable buffer = getText();
        TokenSpan[] allSpans = getSpans(buffer);
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String nonspannableText = getNonSpannableText();

        for (TokenSpan span : allSpans) {
            span.setDeleteMode(false);
            int start = buffer.getSpanStart(span);
            int end = buffer.getSpanEnd(span);
            ssb.append(span.getText());
            ssb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        notifyTextWatcher = false;
        setText(ssb);
        notifyTextWatcher = true;
        append(nonspannableText);
        setSelection(getText().length());
    }

    /**
     * Toggles delete mode of selected span
     *
     * @param spanId
     */
    private void toggleSpanDeleteMode(String spanId) {
        Editable buffer = getText();
        TokenSpan[] allSpans = getSpans(buffer);
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String nonspannableText = getNonSpannableText();

        for (TokenSpan span : allSpans) {
            // Toggle delete mode for selected span
            if (span.getId().equals(spanId)) {
                span.setDeleteMode(!span.getDeleteMode());
            }
            // By default, delete mode is false
            else {
                span.setDeleteMode(false);
            }

            int start = buffer.getSpanStart(span);
            int end = buffer.getSpanEnd(span);
            ssb.append(span.getText());
            ssb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        notifyTextWatcher = false;
        setText(ssb);
        notifyTextWatcher = true;
        append(nonspannableText);
        setSelection(getText().length());
    }


    /*
    Fix for AN-5170. The original SpannableStringBuilder.getSpans method may return spans out of order.
    The standard way to deal with it would be to use SpannableStringBuilder.nextSpanTransition to get
    the start positions of the consecutive spans. But sometimes we need actual spans objects, not just
    their positions, and in other cases we need pairs (start, end), which would have to be computed from
    consecutive starts. Another solution is simply to sort the spans before using them. Usually the array
    is only a couple of elements long, so it shouldn't make a performance impact.
     */

    private TokenSpan[] getSpans(Editable buffer) {
        return getSpans(buffer, 0, buffer.length());
    }

    private TokenSpan[] getSpans(final Editable buffer, int start, int end) {
        TokenSpan[] spans = buffer.getSpans(start, end, TokenSpan.class);
        Arrays.sort(spans, new Comparator<TokenSpan>() {
            @Override
            public int compare(TokenSpan o1, TokenSpan o2) {
                return buffer.getSpanStart(o1) - buffer.getSpanStart(o2);
            }
        });
        return spans;
    }
}
