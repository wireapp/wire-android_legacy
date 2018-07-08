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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import com.waz.utils.wrappers.AndroidURI;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

public class StringUtils {

    private static Paint paint = new Paint();

    public static boolean isBlank(CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String capitalise(String string) {
        if (isBlank(string)) {
            return string;
        }
        return string.substring(0, 1).toUpperCase(Locale.getDefault()) + string.substring(1);
    }

    public static String formatTimeSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    public static String formatTimeMilliSeconds(long totalMilliSeconds) {
        long totalSeconds = totalMilliSeconds / 1000;
        return formatTimeSeconds(totalSeconds);
    }

    public static URI normalizeUri(URI uri) {
        if (uri == null) {
            return uri;
        }
        URI normalized = uri.normalizeScheme();
        if (normalized.getAuthority() != null) {
            normalized = new AndroidURI(AndroidURIUtil.unwrap(normalized)
                .buildUpon()
                .encodedAuthority(normalized.getAuthority().toLowerCase(Locale.getDefault()))
                .build());
        }
        return AndroidURIUtil.parse(trimLinkPreviewUrls(normalized));
    }

    public static String trimLinkPreviewUrls(URI uri) {
        if (uri == null) {
            return "";
        }
        String str = uri.toString();
        str = stripPrefix(str, "http://");
        str = stripPrefix(str, "https://");
        str = stripPrefix(str, "www\\.");
        str = stripSuffix(str, "/");
        return str;
    }

    public static String stripPrefix(String str, String prefixRegularExpression) {
        String regex = "^" + prefixRegularExpression;
        String[] matches = str.split(regex);
        if (matches.length >= 2) {
            return matches[1];
        }
        return str;
    }

    public static String stripSuffix(String str, String suffixRegularExpression) {
        String regex = suffixRegularExpression + "$";
        String[] matches = str.split(regex);
        if (matches.length > 0) {
            return matches[0];
        }
        return str;
    }

    public static boolean isRTL() {
        return isRTL(Locale.getDefault());
    }

    public static boolean isRTL(Locale locale) {
        final int directionality = Character.getDirectionality(locale.getDisplayName().charAt(0));
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
               directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
    }

    public static String formatHandle(String username) {
        if (StringUtils.isBlank(username)) {
            return "";
        }
        return "@" + username;
    }

    public static String truncate(String base, int limit) {
        return base.substring(0, Math.min(limit, base.length()));
    }

    public static class TextDrawing {
        private final Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ALPHA_8);
        private final Canvas canvas = new Canvas(bitmap);
        private final ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount());

        public void set(String text) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawText(text, 0, 50 / 2, paint);
            buffer.rewind();
            bitmap.copyPixelsToBuffer(buffer);
        }

        @Override
        public boolean equals(Object o) {
            return o != null && (o instanceof TextDrawing) && Arrays.equals(buffer.array(), ((TextDrawing) o).buffer.array());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(buffer.array());
        }
    }
}
