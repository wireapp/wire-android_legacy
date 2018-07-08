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

import android.graphics.Typeface;
import com.waz.zclient.ui.text.TypefaceFactory;

public class TypefaceUtils {

    /**
     * Returns a typeface with a given name
     */
    public static Typeface getTypeface(String name) {
        if (name == null || "".equals(name)) {
            return null;
        }

        try {
            return TypefaceFactory.getInstance().getTypeface(name);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String getGlyphsTypefaceName() {
        return "wire_glyphs.otf";
    }

    public static String getRedactedTypedaceName() {
        return "redacted_script_regular.ttf";
    }
}
