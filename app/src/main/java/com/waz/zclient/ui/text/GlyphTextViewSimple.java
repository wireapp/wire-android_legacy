/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import com.waz.zclient.ui.utils.TypefaceUtils;

public class GlyphTextViewSimple extends AppCompatTextView {

    public GlyphTextViewSimple(Context context) {
        super(context);
        init();
    }

    public GlyphTextViewSimple(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlyphTextViewSimple(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName()));
        setSoundEffectsEnabled(false);
    }
}
