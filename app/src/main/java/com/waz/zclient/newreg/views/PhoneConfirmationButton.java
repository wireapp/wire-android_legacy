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
package com.waz.zclient.newreg.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.GlyphTextView;


public class PhoneConfirmationButton extends GlyphTextView {

    private final int errorCircleRadius;
    private int cornerRadius;
    private State state;
    private int accentColor;
    private Paint circlePaint;
    private String nextGlyph;

    public PhoneConfirmationButton(Context context) {
        this(context, null);
    }

    public PhoneConfirmationButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PhoneConfirmationButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.FILL);
        nextGlyph = getResources().getString(R.string.glyph__next);

        cornerRadius = getResources().getDimensionPixelSize(R.dimen.new_reg__phone_number__corner_radius);
        errorCircleRadius = getResources().getDimensionPixelSize(R.dimen.new_reg__sign_up__invalid_phone__circle_radius);

        setState(State.NONE);
    }

    public void setAccentColor(int color) {
        accentColor = color;
        circlePaint.setColor(color);
    }

    public void setState(State state) {
        this.state = state;

        switch (state) {
            case NONE:
                setBackground(null);
                setText("");
                setClickable(false);
                setVisibility(View.GONE);
                break;
            case INVALID:
                setBackground(null);
                setText("");
                setClickable(false);
                setVisibility(View.VISIBLE);
                break;
            case CONFIRM:
                setClickable(true);
                GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                                                                         new int[] {accentColor, accentColor, accentColor});
                gradientDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                gradientDrawable.setCornerRadii(new float[] {0, 0, cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0, 0});
                setBackground(gradientDrawable);
                setText(nextGlyph);
                setVisibility(View.VISIBLE);
                break;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (state == State.INVALID) {
            canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, errorCircleRadius, circlePaint);
        }
    }

    public enum State {
        NONE,
        INVALID,
        CONFIRM
    }
}
