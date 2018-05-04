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
package com.wire.testinggallery.precondition;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.ContentValues.TAG;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.wire.testinggallery.DocumentResolver.WIRE_TESTING_FILES_DIRECTORY;
import static com.wire.testinggallery.utils.InfoDisplayManager.showToast;

public class PreconditionsManager {
    private static final String EXPECTED_PACKAGE_NAME = "com.wire.testinggallery";
    private static final String GET_DOCUMENT_ACTION = "com.wire.testing.GET_DOCUMENT";
    private static final String RECORD_VIDEO_ACTION = "android.media.action.VIDEO_CAPTURE";
    private static final String DOCUMENT_RECEIVER_ACTION = "android.intent.action.SEND";
    private static final String DOCUMENT_RECEIVER_TYPE = "application/octet-stream";
    private static final int TESTING_GALLERY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 23456789;
    private static final int TESTING_GALLERY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 23456790;

    static boolean isLockScreenDisabled(Context context) {
        // Starting with android 6.0 calling isLockScreenDisabled fails altogether because the
        // signature has changed. There is a new method isDeviceSecure which, however, does
        // not allow the differentiation between lock screen 'None' and 'Swipe.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            KeyguardManager keyguardMgr = (KeyguardManager) context
                .getSystemService(Context.KEYGUARD_SERVICE);

            // But luckily there is no 'Automatically lock x minutes after sleep' option when
            // 'Swipe' is set which means that as soon as the screen is off, switching back on
            // requires a swipe which results in a USER_PRESENT broadcast.
            if (keyguardMgr != null) {
                return !keyguardMgr.isDeviceSecure();
            }
        }

        String LOCKSCREEN_UTILS = "com.android.internal.widget.LockPatternUtils";

        try {
            Class<?> lockUtilsClass = Class.forName(LOCKSCREEN_UTILS);

            Object lockUtils = lockUtilsClass.getConstructor(Context.class).newInstance(context);

            Method method = lockUtilsClass.getMethod("isLockScreenDisabled");

            // Starting with android 5.x this fails with InvocationTargetException
            // (caused by SecurityException - MANAGE_USERS permission is required because
            //  internally some additional logic was added to return false if one can switch between several users)
            // if (Screen Lock is None) {
            //   ... exception caused by getting all users (if user count)
            // } else {
            //   return false;
            // }
            // -> therefore if no exception is thrown, we know the screen lock setting is
            //    set to Swipe, Pattern, PIN/PW or something else other than 'None'

            boolean isDisabled;
            try {

                isDisabled = Boolean.valueOf(String.valueOf(method.invoke(lockUtils)));
            } catch (InvocationTargetException ex) {
                Log.w(TAG, "Expected exception with screen lock type equals 'None': " + ex);
                isDisabled = true;
            }
            return isDisabled;
        } catch (Exception e) {
            Log.e(TAG, "Error detecting whether screen lock is disabled: " + e);

            e.printStackTrace();
        }

        return false;
    }

    static boolean isDefaultGetDocumentResolver(Context context) {
        Intent intent = new Intent(GET_DOCUMENT_ACTION);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setType("*/*");
        String packageForIntent = getPackageForIntent(context, intent);
        return packageForIntent.equals(EXPECTED_PACKAGE_NAME);
    }

    static void fixDefaultGetDocumentResolver(Context context) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(GET_DOCUMENT_ACTION);
        sendIntent.setType("text/plain");
        context.startActivity(sendIntent);
    }

    static boolean isDefaultVideoRecorder(Context context) {
        Intent intent = new Intent(RECORD_VIDEO_ACTION);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        String packageForIntent = getPackageForIntent(context, intent);
        return packageForIntent.equals(EXPECTED_PACKAGE_NAME);
    }

    static void fixDefaultVideoRecorder(Context context) {
        Intent intent = new Intent(RECORD_VIDEO_ACTION);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        context.startActivity(intent);
    }

    @NonNull
    private static String getPackageForIntent(Context context, Intent intent) {
        ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null) {
            return "";
        }
        return resolveInfo.activityInfo.packageName;
    }

    static boolean hasNotificationAccess(Context context) {
        Set<String> enabledListenerPackages = NotificationManagerCompat.getEnabledListenerPackages(context);
        return enabledListenerPackages.contains(EXPECTED_PACKAGE_NAME);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static void requestSilentlyRights(Activity activity) {
        Context context = activity.getApplicationContext();
        if (ActivityCompat.checkSelfPermission(context, READ_EXTERNAL_STORAGE) !=
            PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{READ_EXTERNAL_STORAGE},
                TESTING_GALLERY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }
        if (ActivityCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE) !=
            PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{WRITE_EXTERNAL_STORAGE},
                TESTING_GALLERY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    static void createDirectory(Context context) {
        if (!WIRE_TESTING_FILES_DIRECTORY.exists() && !WIRE_TESTING_FILES_DIRECTORY.mkdirs()) {
            showToast(context, "Unable to create directory for testing files!!");
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    static boolean checkRights(Activity activity) {
        Context context = activity.getApplicationContext();
        if (ActivityCompat.checkSelfPermission(context, READ_EXTERNAL_STORAGE) !=
            PERMISSION_GRANTED) {
            return false;
        }
        if (ActivityCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE) !=
            PERMISSION_GRANTED) {
            return false;
        }
        return true;
    }

    static boolean checkDirectory(Activity activity) {
        return WIRE_TESTING_FILES_DIRECTORY.exists();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    static void fixNotificationAccess(Activity activity) {
        Intent notificationAccessIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        activity.startActivity(notificationAccessIntent);
    }

    static void fixLockScreen(Activity activity) {
        Intent notificationAccessIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
        activity.startActivity(notificationAccessIntent);
    }

    public static int getBrightness(Activity activity) throws Settings.SettingNotFoundException {
        ContentResolver contentResolver = activity.getContentResolver();
        return android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS);
    }

    @TargetApi(Build.VERSION_CODES.M)
    static void setMinBrightness(Activity activity) {
        if (!Settings.System.canWrite(activity)) {
            //http://developer.android.com/reference/android/provider/Settings.html#ACTION_MANAGE_WRITE_SETTINGS
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            activity.startActivity(intent);
            return;
        }
        int desiredBrightness = 1;
        ContentResolver contentResolver = activity.getContentResolver();
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);  //this will set the manual mode (set the automatic mode off)
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, desiredBrightness);  //this will set the brightness to maximum (255)
    }

    static int getStayAwake(Activity activity) throws Settings.SettingNotFoundException {
        return Settings.Global.getInt(activity.getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
    }

    static void goToStayAwake(Activity activity) {
        Intent devOptions = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        activity.startActivity(devOptions);
    }

    static boolean isDefaultDocumentReceiver(Context context) {
        Intent intent = new Intent(DOCUMENT_RECEIVER_ACTION);
        intent.setType(DOCUMENT_RECEIVER_TYPE);
        String packageForIntent = getPackageForIntent(context, intent);
        return packageForIntent.equals(EXPECTED_PACKAGE_NAME);
    }

    static void fixDefaultDocumentReceiver(Context context) {
        Intent intent = new Intent(DOCUMENT_RECEIVER_ACTION);
        intent.setType(DOCUMENT_RECEIVER_TYPE);
        context.startActivity(intent);
    }
}
