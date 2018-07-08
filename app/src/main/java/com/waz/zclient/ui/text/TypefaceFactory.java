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

import android.graphics.Typeface;

public class TypefaceFactory {

    private static TypefaceFactory typefaceFactory;
    private TypefaceLoader typefaceLoader;

    private TypefaceFactory() {
    }

    public void init(TypefaceLoader typefaceLoader) {
        this.typefaceLoader = typefaceLoader;
    }

    public static TypefaceFactory getInstance() {
        if (typefaceFactory == null) {
            typefaceFactory = new TypefaceFactory();
        }
        return typefaceFactory;
    }

    /**
     * Returns a typeface with a given name
     */
    public Typeface getTypeface(String name) {
        if (typefaceLoader == null) {
            throw new IllegalStateException("Init not called with a valid Typefaceloader");
        }
        return typefaceLoader.getTypeface(name);
    }
}
