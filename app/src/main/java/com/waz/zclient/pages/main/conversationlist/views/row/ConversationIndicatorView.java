/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.conversationlist.views.row;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversation.ConversationUtils;
import com.waz.zclient.ui.text.GlyphTextView;

public class ConversationIndicatorView extends GlyphTextView {

    private Paint paint;
    private State state;
    private int centerX;
    private int centerY;
    private int unreadSize;
    private int pendingRadius;
    private int unsentRadius;
    private int strokeWidth;
    private int accentColor;

    public enum State {
        PENDING,
        UNREAD,
        UNSENT
    }

    public ConversationIndicatorView(Context context) {
        super(context);
        init();
    }

    public ConversationIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ConversationIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        state = State.UNREAD;

        setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.list__unsent_indicator_text_size));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            setTextColor(getResources().getColor(R.color.list__unsent_indicator_text_color));
        } else {
            setTextColor(getResources().getColor(R.color.list__unsent_indicator_text_color, getContext().getTheme()));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setTextAlignment(TEXT_ALIGNMENT_CENTER);
        }
        setGravity(Gravity.CENTER);

        pendingRadius = ConversationUtils.getMaxIndicatorRadiusPx(getContext());
        unsentRadius = getResources().getDimensionPixelSize(R.dimen.list__unsent_indicator_radius);
        strokeWidth = getResources().getDimensionPixelSize(R.dimen.list_pending_connect_request_indicator__stroke_width);
    }

    public void setUnreadSize(int unreadSize) {
        this.unreadSize = unreadSize;
        invalidate();
    }

    public void setState(State state) {
        this.state = state;

        switch (state) {
            case PENDING:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(accentColor);
                setText("");
                break;
            case UNREAD:
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(accentColor);
                setText("");
                break;
            case UNSENT:
                paint.setStyle(Paint.Style.FILL);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    //noinspection deprecation
                    paint.setColor(getResources().getColor(R.color.list__unsent_indicator_color));
                } else {
                    paint.setColor(getResources().getColor(R.color.list__unsent_indicator_color, getContext().getTheme()));
                }
                setText(R.string.glyph__attention);
                break;
        }

        invalidate();
    }

    public void setAccentColor(int color) {
        accentColor = color;
        if (state != State.UNSENT) {
            paint.setColor(accentColor);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int actualWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int actualHeight = MeasureSpec.getSize(heightMeasureSpec) - getPaddingBottom() - getPaddingTop();

        centerX = getPaddingLeft() + actualWidth / 2;
        centerY = getPaddingTop() + actualHeight / 2;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float radius = 0;
        switch (state) {
            case PENDING:
                radius = pendingRadius;
                break;
            case UNREAD:
                radius = unreadSize;
                break;
            case UNSENT:
                radius = unsentRadius;
                break;
        }
        canvas.drawCircle(centerX, centerY, radius, paint);
        super.onDraw(canvas);
    }
}
