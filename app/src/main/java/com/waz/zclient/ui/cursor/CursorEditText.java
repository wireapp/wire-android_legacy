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
package com.waz.zclient.ui.cursor;


import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.TypefaceEditText;
import timber.log.Timber;

import java.lang.reflect.Field;

public class CursorEditText extends TypefaceEditText {

    private SelectionChangedCallback callback = null;

    public CursorEditText(Context context) {
        super(context);
    }

    public CursorEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CursorEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /*
        Hack to keep the implementation consistent....unluckily TypefaceEditText derives from
        AccentColorEditText and the updateCursor is causing the Cursor to disappear. This hack
        prohibits this method to be executed.
     */
    @Override
    protected void updateCursor() {

    }

    public void setAccentColor(int accentColor) {
        try {
            Field cursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            cursorDrawableRes.setAccessible(true);
            cursorDrawableRes.set(this, matchZColorToCursorResourceID(accentColor));
        } catch (Exception e) {
            Timber.e(e, "Failed setting custom cursor color");
        }
    }

    public static int matchZColorToCursorResourceID(int color) {
        switch (color) {
            case 0xFFFF5000:
                return R.drawable.cursor_ephemeral;
            case -16726016:
                return R.drawable.cursor_green; // green 51200 #00C800
            case -106819:
                return R.drawable.cursor_pink; // pink 16670397 #FE5EBD
            case -11776:
                return R.drawable.cursor_yellow; // yellow 16765440 #FFD200
            case -55552:
                return R.drawable.cursor_red; // red 16721664 #FF2700
            case -27136:
                return R.drawable.cursor_orange; // orange 16750080 #FF9600
            case -6487809:
                return R.drawable.cursor_magenta; // magenta 10289407 #9D00FF
            default:
                return R.drawable.cursor_blue; // if the match is not found, resort to default cursor
        }
    }


    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection conn = super.onCreateInputConnection(outAttrs);
        outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        return conn;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (callback != null) {
            callback.onSelectionChanged(selStart, selEnd);
        }
    }

    public void setSelectionChangedCallback(SelectionChangedCallback callback) {
        this.callback = callback;
    }

    public interface SelectionChangedCallback {
        void onSelectionChanged(int selStart, int selEnd);
    }
}
