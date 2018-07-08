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
package com.waz.zclient.utils;

import android.content.Context;
import com.waz.zclient.R;
import timber.log.Timber;

public enum LayoutSpec {
    LAYOUT_PHONE(320),
    LAYOUT_KINDLE(480),
    LAYOUT_7_INCH(600),
    LAYOUT_10_INCH(720),
    ERROR_GETTING_SPEC(0);
    private final int spec;

    LayoutSpec(int spec) {
        this.spec = spec;
    }

    private static LayoutSpec get(Context context) {

        if (context == null) {
            Timber.e("Tried to get LayoutSpec with a null context!");
            return ERROR_GETTING_SPEC;
        }

        int currentSpec = context.getResources().getInteger(R.integer.layout__spec);
        if (currentSpec == LAYOUT_PHONE.spec) {
            return LAYOUT_PHONE;
        } else if (currentSpec == LAYOUT_KINDLE.spec) {
            return LAYOUT_KINDLE;
        } else if (currentSpec == LAYOUT_7_INCH.spec) {
            return LAYOUT_7_INCH;
        } else if (currentSpec == LAYOUT_10_INCH.spec) {
            return LAYOUT_10_INCH;
        } else {
            throw new IllegalStateException("Layout has to specified for " + currentSpec);
        }
    }

    public static boolean isPhone(Context context) {
        return get(context) == LAYOUT_PHONE;
    }
}
