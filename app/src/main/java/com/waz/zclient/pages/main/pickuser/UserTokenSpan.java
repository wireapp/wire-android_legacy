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
package com.waz.zclient.pages.main.pickuser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import com.waz.zclient.R;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.ui.utils.TypefaceUtils;
import com.waz.zclient.ui.text.SpannableEditText;

public class UserTokenSpan extends SpannableEditText.TokenSpan {
    private final String userId;
    private final String userName;
    private final String deleteIconString;

    private int lineWidth;

    private float spanWidth;
    private Rect textBounds;
    private float textWidth;
    private Rect deleteIconBounds;

    private int paddingHorizontal;
    private int paddingRightFix;
    private int textOffsetTop;
    private int spaceForCursor;

    private float backgroundRadius;
    private float backgroundHeight;
    private float backgroundBottomPadding;

    // When true, shows cross button and truncates text
    private boolean deleteMode;
    // make clickable area slightly bigger than delete button

    private TextPaint textPaint;
    private TextPaint deleteModeTextPaint;
    private Paint backgroundPaint;

    public UserTokenSpan(String userId, String userName, Context context, boolean deleteMode, int lineWidth) {
        this.userId = userId;
        this.userName = userName;
        this.deleteMode = deleteMode;
        this.lineWidth = lineWidth;

        paddingHorizontal = context.getResources().getDimensionPixelSize(R.dimen.people_picker__input_person_token__padding_horizontal);
        paddingRightFix = context.getResources().getDimensionPixelSize(R.dimen.people_picker__input_person_token__padding_right_fix);
        textOffsetTop = 0;
        spaceForCursor = context.getResources().getDimensionPixelSize(R.dimen.people_picker__input_person_token__extra_space_for_cursor);
        backgroundHeight = context.getResources().getDimensionPixelSize(R.dimen.wire__padding__16);
        backgroundBottomPadding = context.getResources().getDimensionPixelSize(R.dimen.wire__padding__4);
        backgroundRadius = backgroundBottomPadding + backgroundHeight / 2;

        int tokenBackgroundColor;
        int textColor;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            tokenBackgroundColor = context.getResources().getColor(R.color.people_picker__input_person_token__background_color);
            //noinspection deprecation
            textColor = context.getResources().getColor(R.color.text__primary_light);
        } else {
            tokenBackgroundColor = context.getResources().getColor(R.color.people_picker__input_person_token__background_color, context.getTheme());
            textColor = context.getResources().getColor(R.color.text__primary_light, context.getTheme());
        }
        int textSize = context.getResources().getDimensionPixelSize(R.dimen.wire__text_size__regular);

        // Paints
        textPaint = new TextPaint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(TypefaceUtils.getTypeface(context.getResources().getString(R.string.wire__typeface__regular)));

        deleteModeTextPaint = new TextPaint();
        deleteModeTextPaint.setAntiAlias(true);
        deleteModeTextPaint.setColor(tokenBackgroundColor);
        deleteModeTextPaint.setTextSize(textSize);
        deleteModeTextPaint.setTypeface(TypefaceUtils.getTypeface(context.getResources().getString(R.string.wire__typeface__light)));

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setAntiAlias(true);

        // Measure bounds of text and delete icon
        textBounds = new Rect();
        textPaint.getTextBounds(userName, 0, userName.length(), textBounds);
        textWidth = textPaint.measureText(userName, 0, userName.length());

        deleteIconString = context.getResources().getString(R.string.glyph__close);
        deleteIconBounds = new Rect();
        textPaint.getTextBounds(deleteIconString, 0, 1, deleteIconBounds);
    }

    public void setTextColor(int textColor) {
        textPaint.setColor(textColor);
    }

    public void setDeleteModeTextColor(int color) {
        deleteModeTextPaint.setColor(color);
    }

    @Override
    public void draw(Canvas canvas,
                     CharSequence text,
                     int start,
                     int end,
                     float x,
                     int top,
                     int y,
                     int bottom,
                     Paint paint) {

        float textPosX = x + paddingHorizontal;
        float textPosY = y + textOffsetTop;

        CharSequence truncatedText = TextUtils.ellipsize(userName,
            textPaint,
            availableTextSpace(),
            TextUtils.TruncateAt.END);

        canvas.drawRoundRect(
            new RectF(textPosX - paddingHorizontal,
                textPosY - backgroundHeight,
                textPosX + textBounds.width() + paddingHorizontal,
                textPosY + backgroundBottomPadding),
            backgroundRadius, backgroundRadius, backgroundPaint);

        if (deleteMode) {
            canvas.drawText(truncatedText.toString(), textPosX, textPosY, deleteModeTextPaint);
        } else {
            canvas.drawText(truncatedText.toString(), textPosX, textPosY, textPaint);
        }
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (MathUtils.floatEqual(spanWidth, 0f)) {
            spanWidth = tokenWidth();
        }

        return (int) spanWidth;
    }

    public String getId() {
        return userId;
    }

    public String getText() {
        return userName;
    }

    public Boolean getDeleteMode() {
        return deleteMode;
    }

    public void setDeleteMode(boolean deleteMode) {
        this.deleteMode = deleteMode;
    }

    /**
     * @param clickPosition X position of click inside this span
     * @return Returns true if this span was clicked
     */
    public Boolean spanClicked(int clickPosition) {
        return clickPosition <= spanWidth;
    }

    private float tokenWidth() {
        float tokenWidth = textWidth + 2 * paddingHorizontal + paddingRightFix;

        // Ensure token fits into one line
        float totalWidth = tokenWidth;
        if (totalWidth > lineWidth) {
            tokenWidth = lineWidth - spaceForCursor;
        }

        return tokenWidth;
    }

    private float availableTextSpace() {
        return tokenWidth() - 2 * paddingHorizontal - paddingRightFix;
    }
}
