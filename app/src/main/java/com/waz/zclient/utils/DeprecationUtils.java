/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.PowerManager;
import android.os.Vibrator;
import android.telephony.PhoneNumberUtils;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;
import androidx.core.view.ViewCompat;

@SuppressWarnings("Deprecation")
/*
 This class exists to facilitate fine-grained warning deprecation, not possible in Scala
 */
public class DeprecationUtils {

    public static int FLAG_TURN_SCREEN_ON = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

    public static int FLAG_SHOW_WHEN_LOCKED = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

    public static int WAKE_LOCK_OPTIONS = PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
        PowerManager.FULL_WAKE_LOCK |
        PowerManager.ACQUIRE_CAUSES_WAKEUP;

    public static int FLAG_DISMISS_KEYGUARD = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;

    public static Drawable getDrawable(Context context, int resId) {
        return context.getResources().getDrawable(resId);
    }

    public static String formatNumber(String s) {
        return PhoneNumberUtils.formatNumber(s);
    }

    public static float getAlpha(View v) {
        return ViewCompat.getAlpha(v);
    }

    public static void setAlpha(View v, float f) {
        ViewCompat.setAlpha(v, f);
    }

    public static NotificationCompat.Builder getBuilder(Context context) {
        return new NotificationCompat.Builder(context);
    }

    //maybe we can get rid of this one?
    public static void vibrate(Vibrator v, long[] pattern, int repeat) {
        v.vibrate(pattern, repeat);
    }

    public static void addCompletedDownload(Context context, String name, String mime, String path, long size) {
        DownloadManager manager = (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        manager.addCompletedDownload(name, name, false, mime, path, size, true);
    }

    public static String MEDIA_COLUMN_DATA = "_data";

    /**
     * This function is taken from this Stackoverflow answer:
     * https://stackoverflow.com/a/39841101/158703
     */
    public static Spanned fromHtml(String source) {
        return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY);
    }
}

