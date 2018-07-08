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
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;

public class RingtoneUtils {

    private RingtoneUtils() {
    }

    public static boolean isDefaultValue(@NonNull Context context, @NonNull  String uri, @RawRes int rawId) {
        return Uri.parse(uri).compareTo(getUriForRawId(context, rawId)) == 0;
    }

    public static Uri getUriForRawId(@NonNull Context context, @RawRes int rawId) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + rawId);
    }

    public static String getSilentValue() {
        return "silent";
    }

    public static boolean isSilent(String value) {
        return getSilentValue().equals(value);
    }
}
