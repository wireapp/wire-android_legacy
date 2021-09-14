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
import androidx.annotation.Nullable;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;
import com.waz.zclient.controllers.notifications.ShareSavedImageActivity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IntentUtils {

    public static final String WIRE_SCHEME = "wire";
    public static final String PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN = "password-reset-successful";
    public static final String EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION = "EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION";
    public static final String EXTRA_CONTENT_URI = "EXTRA_CONTENT_URI";
    private static final String MAP_INTENT_URI = "geo:0,0?q=%f,%f&z=%d"; // osmand
    private static final String MAP_WITH_LABEL_INTENT_URI = "geo:0,0?q=%f,%f(%s)&z=%d"; // google maps
    private static final String GOOGLE_MAPS_INTENT_PACKAGE = "com.google.android.apps.maps";
    private static final String GOOGLE_MAPS_WEB_LINK = "https://maps.google.com/maps?z=%d&q=loc:%f+%f+(%s)"; // FIXME
    private static final String OSMAND_INTENT_PACKAGE = "net.osmand";
    private static final String OSMAND_PLUS_INTENT_PACKAGE = "net.osmand.plus";
    private static final String OPENSTREETMAP_WEB_LINK = "https://www.openstreetmap.org/#map=%d/%f/%f";
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
        galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return PendingIntent.getActivity(context, 0, galleryIntent, 0);
    }

    public static PendingIntent getPendingShareIntent(Context context, URI uri) {
        Intent shareIntent = new Intent(context, ShareSavedImageActivity.class);
        shareIntent.putExtra(IntentUtils.EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION, true);
        shareIntent.putExtra(IntentUtils.EXTRA_CONTENT_URI, uri.toString());
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
            to = new String[]{BuildConfig.SUPPORT_EMAIL};
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
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(shareIntent,
                                    context.getString(R.string.notification__image_saving__action__share));
    }

    public static boolean isLaunchFromSaveImageNotificationIntent(@Nullable Intent intent) {
        return intent != null &&
               intent.getBooleanExtra(EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION, false) &&
               intent.hasExtra(EXTRA_CONTENT_URI);
    }

    // FIXME: osm vs gmaps
    public static List<Intent> getMapIntents(Context context, float lat, float lon, int zoom, String name) {
        List<Intent> intents = new ArrayList<>();
        Uri osmUri = Uri.parse(String.format(Locale.getDefault(), MAP_INTENT_URI, lat, lon, zoom));
        intents.add(new Intent(Intent.ACTION_VIEW, osmUri).setPackage(OSMAND_PLUS_INTENT_PACKAGE));
        intents.add(new Intent(Intent.ACTION_VIEW, osmUri).setPackage(OSMAND_INTENT_PACKAGE));
        Uri gmmIntentUri = osmUri;
        if (!StringUtils.isBlank(name)) {
            gmmIntentUri = Uri.parse(String.format(Locale.getDefault(), MAP_WITH_LABEL_INTENT_URI, lat, lon, name, zoom));
        }
        intents.add(new Intent(Intent.ACTION_VIEW, gmmIntentUri).setPackage(GOOGLE_MAPS_INTENT_PACKAGE));
        String url = String.format(Locale.getDefault(), OPENSTREETMAP_WEB_LINK, zoom, lat, lon);
        intents.add(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return intents;
    }

    public static Intent getInviteIntent(String subject, String body) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        return intent;
    }
}
