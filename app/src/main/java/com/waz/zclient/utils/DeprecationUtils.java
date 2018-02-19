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


import android.app.Notification;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.Area;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.ViewCompat;
import android.telephony.PhoneNumberUtils;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.view.WindowManager;

import net.hockeyapp.android.CrashManagerListener;
import net.hockeyapp.android.ExceptionHandler;

import java.util.Locale;

import static android.hardware.Camera.getNumberOfCameras;

@SuppressWarnings("Deprecation")
/*
 This class exists to facilitate fine-grained warning deprecation, not possible in Scala
 TODO(AN-5975): rewrite this class in Scala once we can include the silencer plugin
 */
public class DeprecationUtils {

    public static int PRIORITY_MAX = Notification.PRIORITY_MAX;

    public static int WAKE_LOCK_OPTIONS = PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                          PowerManager.FULL_WAKE_LOCK          |
                                          PowerManager.ACQUIRE_CAUSES_WAKEUP;

    public static int FLAG_DISMISS_KEYGUARD = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;

    public static final int NUMBER_OF_CAMERAS = getNumberOfCameras();

    public static Locale getDefaultLocale(Context context) {
        return context.getResources().getConfiguration().locale;
    }

    public static Drawable getDrawable(Context context, int resId) {
        return context.getResources().getDrawable(resId);
    }

    public static void saveException(Throwable t, CrashManagerListener l) {
        ExceptionHandler.saveException(t, l);
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

    public static void setPriority(Notification not, int priority) {
        not.priority = priority;
    }

    //maybe we can get rid of this one?
    public static void vibrate(Vibrator v, long[] pattern, int repeat) {
        v.vibrate(pattern, repeat);
    }

    public static void setParams(Camera c, CameraWrap cw) {
        if (c != null) {
            Parameters params = c.getParameters();
            cw.f(new CameraParamsWrapper(params));
            c.setParameters(params);
        }
    }

    public static void setAutoFocusCallback(Camera c, final AutoFocusCallbackDeprecation af) {
        c.autoFocus(new AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                af.onAutoFocus(success, new CameraWrapper(camera));
            }
        });
    }

    public static Area Area(Rect r, int focusWeight) {
        return new Area(r, focusWeight);
    }

    public static ShutterCallback shutterCallback(final ShutterCallbackDeprecated scd) {
        return new ShutterCallback() {
            @Override
            public void onShutter() {
                scd.onShutter();
            }
        };
    }

    public static void getCameraInfo(int cameraId, CameraInfo info) {
        Camera.getCameraInfo(cameraId, info);
    }

    public static CameraInfo CameraInfoFactory() {
        return new CameraInfo();
    }

    public static PictureCallback pictureCallback(final PictureCallbackDeprecated pcd) {
        return new PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                pcd.onPictureTaken(data, new CameraWrapper(camera));
            }
        };
    }

    /**
     * This function is taken from this Stackoverflow answer:
     * https://stackoverflow.com/a/39841101/158703
     */
    public static Spanned fromHtml(String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(source);
        }
    }

    /**
     * We need to use an interface as Java doesn't have first class functions.
     * We need CameraParamsWrapper so we can instantiate this interface in Scala code
     * without writing the type "Params" as this gives a deprecation warning
     */
    public interface CameraWrap {
        void f(CameraParamsWrapper wrapper);
    }
}

