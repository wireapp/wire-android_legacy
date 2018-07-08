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


import android.text.TextUtils;

import java.util.Locale;

public abstract class TextTransform {

    public abstract CharSequence transform(CharSequence text);

    public static final TextTransform NONE = new TextTransform() {
        @Override
        public CharSequence transform(CharSequence text) {
            return text;
        }
    };

    public static final TextTransform UPPER_CASE = new TextTransform() {
        @Override
        public String transform(CharSequence text) {
            return text != null ? text.toString().toUpperCase(Locale.getDefault()) : null;
        }
    };

    public static final TextTransform LOWER_CASE = new TextTransform() {
        @Override
        public String transform(CharSequence text) {
            return text != null ? text.toString().toLowerCase(Locale.getDefault()) : null;
        }
    };

    public static TextTransform get(String transform) {
        if ("upper".equalsIgnoreCase(transform)) {
            return UPPER_CASE;
        } else if ("lower".equalsIgnoreCase(transform)) {
            return LOWER_CASE;
        } else if (TextUtils.isEmpty(transform)) {
            return NONE;
        } else {
            throw new RuntimeException("Unknown text transform: " + transform);
        }
    }
}
