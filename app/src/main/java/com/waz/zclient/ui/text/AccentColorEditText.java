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
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.AttributeSet;

import com.waz.zclient.ui.utils.ReflectionUtils;
import com.waz.zclient.utils.ViewUtils;

import java.lang.reflect.Field;

import timber.log.Timber;

public class AccentColorEditText extends android.support.v7.widget.AppCompatEditText {

    private static final int DEFAULT_CURSOR_WIDTH_DP = 2;
    private int accentColor = Color.WHITE;

    public AccentColorEditText(Context context) {
        this(context, null);
    }

    public AccentColorEditText(Context context, AttributeSet attrs) {
        //without a defStyleAttr, the EditText will think it doesn't have a cursor
        this(context, attrs, android.R.attr.editTextStyle);
    }

    public AccentColorEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        updateCursor();
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
        updateCursor();
    }

    public int getAccentColor() {
        return accentColor;
    }

    /**
     * Whenever the hint textSize is smaller than the regular textSize for a given EditText, the cursor
     * shrinks to fit the text. This method can be overridden to set the cursor padding so that it remains
     * the full size of the EditText.
     */
    protected void setHintCursorSize(ShapeDrawable cursorDrawable) {
    }

    protected void updateCursor() {
        ShapeDrawable cursorDrawable = new ShapeDrawable(new RectShape());
        cursorDrawable.setIntrinsicWidth(ViewUtils.toPx(getContext(), DEFAULT_CURSOR_WIDTH_DP));
        cursorDrawable.getPaint().setColor(accentColor);
        setHintCursorSize(cursorDrawable);

        try {
            // now attach the resource so it stops using the old one
            Field ef = ReflectionUtils.getInheritedPrivateField(this.getClass(), "mEditor");
            if (ef == null) {
                return;
            }
            ef.setAccessible(true);
            Object editorObject = ef.get(this);
            Field df = ReflectionUtils.getInheritedPrivateField(editorObject.getClass(), "mCursorDrawable");

            if (df == null) {
                return;
            }
            df.setAccessible(true);
            Object dfo = df.get(editorObject);


            if (dfo == null || !(dfo instanceof Drawable[])) {
                return;
            }
            Drawable[] darray = (Drawable[]) dfo;
            for (int i = 0; i < darray.length; i++) {
                darray[i] = cursorDrawable;
            }

        } catch (IllegalAccessException | IllegalArgumentException ex) {
            Timber.e(ex, "Error accessing private field");
        }
    }
}
