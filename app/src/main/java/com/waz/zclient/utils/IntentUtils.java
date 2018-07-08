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

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.support.annotation.Nullable;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;
import com.waz.zclient.controllers.notifications.ShareSavedImageActivity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

public class IntentUtils {

    public static final String WIRE_SCHEME = "wire";
    public static final String PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN = "password-reset-successful";
    public static final String EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION = "EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION";
    private static final String GOOGLE_MAPS_INTENT_URI = "geo:0,0?q=%s,%s";
    private static final String GOOGLE_MAPS_WITH_LABEL_INTENT_URI = "geo:0,0?q=%s,%s(%s)";
    private static final String GOOGLE_MAPS_INTENT_PACKAGE = "com.google.android.apps.maps";
    private static final String GOOGLE_MAPS_WEB_LINK = "http://maps.google.com/maps?z=%d&q=loc:%f+%f+(%s)";
    private static final String IMAGE_MIME_TYPE = "image/*";

    public static boolean isPasswordResetIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }

        Uri data = intent.getData();
        return data != null &&
               WIRE_SCHEME.equals(data.getScheme()) &&
               PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN.equals(data.getHost());
    }

    public static PendingIntent getGalleryIntent(Context context, URI uri) {
        // TODO: AN-2276 - Replace with ShareCompat.IntentBuilder
        Uri androidUri = AndroidURIUtil.unwrap(uri);
        Intent galleryIntent = new Intent(Intent.ACTION_VIEW);
        galleryIntent.setDataAndTypeAndNormalize(androidUri, IMAGE_MIME_TYPE);
        galleryIntent.setClipData(new ClipData(null, new String[] {IMAGE_MIME_TYPE}, new ClipData.Item(androidUri)));
        galleryIntent.putExtra(Intent.EXTRA_STREAM, androidUri);
        return PendingIntent.getActivity(context, 0, galleryIntent, 0);
    }

    public static PendingIntent getPendingShareIntent(Context context, URI uri) {
        Intent shareIntent = new Intent(context, ShareSavedImageActivity.class);
        shareIntent.putExtra(Intent.EXTRA_STREAM, AndroidURIUtil.unwrap(uri));
        shareIntent.putExtra(IntentUtils.EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION, true);
        return PendingIntent.getActivity(context, 0, shareIntent, 0);
    }

    public static Intent getDebugReportIntent(Context context, URI fileUri) {
        String versionName;
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (Exception e) {
            versionName = "n/a";
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("vnd.android.cursor.dir/email");
        String[] to;
        if (BuildConfig.DEVELOPER_FEATURES_ENABLED) {
            to = new String[]{"android@wire.com"};
        } else {
            to = new String[]{"support@wire.com"};
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_EMAIL, to);
        intent.putExtra(Intent.EXTRA_STREAM, AndroidURIUtil.unwrap(fileUri));
        intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.debug_report__body));
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.debug_report__title, versionName));
        return intent;
    }

    public static Intent getSavedImageShareIntent(Context context, URI uri) {
        Uri androidUri = AndroidURIUtil.unwrap(uri);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setClipData(new ClipData(null, new String[] {IMAGE_MIME_TYPE}, new ClipData.Item(androidUri)));
        shareIntent.putExtra(Intent.EXTRA_STREAM, androidUri);
        shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.setDataAndTypeAndNormalize(androidUri, IMAGE_MIME_TYPE);
        return Intent.createChooser(shareIntent,
                                    context.getString(R.string.notification__image_saving__action__share));
    }

    public static boolean isLaunchFromSaveImageNotificationIntent(@Nullable Intent intent) {
        return intent != null &&
               intent.getBooleanExtra(EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION, false);
    }

    public static Intent getGoogleMapsIntent(Context context, float lat, float lon, int zoom, String name) {
        Uri gmmIntentUri;
        if (StringUtils.isBlank(name)) {
            gmmIntentUri = Uri.parse(String.format(GOOGLE_MAPS_INTENT_URI, lat, lon));
        } else {
            gmmIntentUri = Uri.parse(String.format(GOOGLE_MAPS_WITH_LABEL_INTENT_URI, lat, lon, name));
        }
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage(GOOGLE_MAPS_INTENT_PACKAGE);
        if (mapIntent.resolveActivity(context.getPackageManager()) == null) {
            return getGoogleMapsWebFallbackIntent(lat, lon, zoom, name);
        }
        return mapIntent;
    }

    private static Intent getGoogleMapsWebFallbackIntent(float lat, float lon, int zoom, String name) {
        String urlEncodedName;
        try {
            urlEncodedName = URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            urlEncodedName = name;
        }
        String url = String.format(Locale.getDefault(), GOOGLE_MAPS_WEB_LINK, zoom, lat, lon, urlEncodedName);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return browserIntent;
    }

    public static Intent getInviteIntent(String subject, String body) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        return intent;
    }
}
