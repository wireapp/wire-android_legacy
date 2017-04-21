/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.waz.api.EphemeralExpiration;
import com.waz.utils.wrappers.AndroidURI;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.LaunchActivity;
import com.waz.zclient.MainActivity;
import com.waz.zclient.PopupActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.notifications.ShareSavedImageActivity;
import hugo.weaving.DebugLog;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class IntentUtils {

    public static final String WIRE_SCHEME = "wire";
    public static final String EMAIL_VERIFIED_HOST_TOKEN = "email-verified";
    public static final String PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN = "password-reset-successful";
    public static final String SMS_CODE_TOKEN = "verify-phone";
    public static final String INVITE_HOST_TOKEN = "connect";
    public static final String APP_PAGE_HOST_TOKEN = "app-page";
    public static final String EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION = "EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION";
    private static final String EXTRA_LAUNCH_CONVERSATION_IDS = "EXTRA_LAUNCH_CONVERSATION_IDS";
    private static final String EXTRA_LAUNCH_FROM_NOTIFICATION = "EXTRA_LAUNCH_FROM_NOTIFICATION";
    private static final String EXTRA_LAUNCH_FROM_SHARING = "EXTRA_LAUNCH_FROM_SHARING";
    private static final String EXTRA_LAUNCH_CONVERSATION_MESSAGE = "EXTRA_LAUNCH_CONVERSATION_MESSAGE";
    private static final String EXTRA_LAUNCH_CONVERSATION_FILES = "EXTRA_LAUNCH_CONVERSATION_FILES";
    private static final String EXTRA_LAUNCH_START_CALL = "EXTRA_LAUNCH_START_CALL";
    private static final String EXTRA_LAUNCH_EPHEMERAL_EXPIRATION = "EXTRA_LAUNCH_EPHEMERAL_EXPIRATION";
    public static final String LOCALYTICS_DEEPLINK_SETTINGS = "settings";
    public static final String LOCALYTICS_DEEPLINK_SEARCH = "search";
    public static final String LOCALYTICS_DEEPLINK_PROFILE = "profile";
    private static final String GOOGLE_MAPS_INTENT_URI = "geo:0,0?q=%s,%s";
    private static final String GOOGLE_MAPS_WITH_LABEL_INTENT_URI = "geo:0,0?q=%s,%s(%s)";
    private static final String GOOGLE_MAPS_INTENT_PACKAGE = "com.google.android.apps.maps";
    private static final String GOOGLE_MAPS_WEB_LINK = "http://maps.google.com/maps?z=%d&q=loc:%f+%f+(%s)";
    private static final String IMAGE_MIME_TYPE = "image/*";

    public static boolean isEmailVerificationIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }

        Uri data = intent.getData();
        return data != null &&
               WIRE_SCHEME.equals(data.getScheme()) &&
               EMAIL_VERIFIED_HOST_TOKEN.equals(data.getHost());
    }

    public static boolean isPasswordResetIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }

        Uri data = intent.getData();
        return data != null &&
               WIRE_SCHEME.equals(data.getScheme()) &&
               PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN.equals(data.getHost());
    }

    public static boolean isInviteIntent(@Nullable Intent intent) {
        String token = getInviteToken(intent);
        return !TextUtils.isEmpty(token);
    }

    public static boolean isSmsIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }

        Uri data = intent.getData();
        return data != null &&
               WIRE_SCHEME.equals(data.getScheme()) &&
               SMS_CODE_TOKEN.equals(data.getHost());
    }

    @DebugLog
    public static String getSmsCode(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        Uri data = intent.getData();
        if (isSmsIntent(intent) &&
            data.getPath() != null &&
            data.getPath().length() > 1
            ) {
            return data.getPath().substring(1);
        }
        return null;
    }

    @DebugLog
    public static String getInviteToken(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        Uri data = intent.getData();
        if (data != null &&
            WIRE_SCHEME.equals(data.getScheme()) &&
            INVITE_HOST_TOKEN.equals(data.getHost())) {
            return data.getQueryParameter("code");
        }
        return null;
    }

    public static String getAppPage(@Nullable Intent intent) {
        if (intent == null ||
            TextUtils.isEmpty(intent.getStringExtra("deeplink"))) {
            return null;
        }

        Uri data = Uri.parse(intent.getStringExtra("deeplink"));
        if (data != null &&
            WIRE_SCHEME.equals(data.getScheme()) &&
            APP_PAGE_HOST_TOKEN.equals(data.getHost())) {
            return data.getQueryParameter("page");
        }
        return null;
    }

    public static Intent resetAppPage(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        intent.putExtra(LaunchActivity.APP_PAGE, "");
        return intent;
    }


    public static boolean isLaunchFromNotificationIntent(@Nullable Intent intent) {
        return intent != null &&
               intent.getBooleanExtra(EXTRA_LAUNCH_FROM_NOTIFICATION, false);
    }

    public static boolean isLaunchFromSharingIntent(@Nullable Intent intent) {
        return intent != null &&
               intent.getBooleanExtra(EXTRA_LAUNCH_FROM_SHARING, false);
    }

    public static void clearLaunchIntentExtra(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        intent.removeExtra(EXTRA_LAUNCH_FROM_SHARING);
        intent.removeExtra(EXTRA_LAUNCH_FROM_NOTIFICATION);
        intent.removeExtra(EXTRA_LAUNCH_START_CALL);
        intent.removeExtra(EXTRA_LAUNCH_CONVERSATION_IDS);
        intent.removeExtra(EXTRA_LAUNCH_CONVERSATION_MESSAGE);
    }

    public static Intent getAppLaunchIntent(@NonNull Context context,
                                            String conversationId,
                                            @Nullable String sharedText) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_LAUNCH_FROM_SHARING, true);
        intent.putExtra(EXTRA_LAUNCH_CONVERSATION_MESSAGE, sharedText != null ? sharedText : "");
        intent.putStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS, new ArrayList<>(Collections.singletonList(conversationId)));
        return intent;
    }

    public static Intent getAppLaunchIntent(@NonNull Context context,
                                            List<String> conversationIds,
                                            @Nullable String sharedText,
                                            EphemeralExpiration expiration) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_LAUNCH_FROM_SHARING, true);
        intent.putExtra(EXTRA_LAUNCH_CONVERSATION_MESSAGE, sharedText != null ? sharedText : "");
        intent.putStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS, new ArrayList<>(conversationIds));
        intent.putExtra(EXTRA_LAUNCH_EPHEMERAL_EXPIRATION, expiration.milliseconds);
        return intent;
    }

    private static ArrayList<Uri> mapToAndroidUris(List<URI> uris) { //NOPMD
        ArrayList<Uri> androidUris = new ArrayList<>(uris.size());
        for (URI uri: uris) {
            androidUris.add(AndroidURIUtil.unwrap(uri));
        }
        return androidUris;
    }

    public static Intent getAppLaunchIntent(@NonNull Context context,
                                                 String conversationId,
                                                 List<URI> sharedFiles,
                                                 EphemeralExpiration expiration) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_LAUNCH_FROM_SHARING, true);

        intent.putParcelableArrayListExtra(EXTRA_LAUNCH_CONVERSATION_FILES, mapToAndroidUris(sharedFiles));
        intent.putStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS, new ArrayList<>(Collections.singletonList(conversationId)));
        intent.putExtra(EXTRA_LAUNCH_EPHEMERAL_EXPIRATION, expiration.milliseconds);
        return intent;
    }

    public static Intent getAppLaunchIntent(@NonNull Context context,
                                            List<String> conversationIds,
                                            List<URI> sharedFiles,
                                            EphemeralExpiration expiration) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_LAUNCH_FROM_SHARING, true);
        intent.putParcelableArrayListExtra(EXTRA_LAUNCH_CONVERSATION_FILES, mapToAndroidUris(sharedFiles));
        intent.putStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS, new ArrayList<>(conversationIds));
        intent.putExtra(EXTRA_LAUNCH_EPHEMERAL_EXPIRATION, expiration.milliseconds);
        return intent;
    }

    public static Intent getAppLaunchIntent(@NonNull Context context, String conversationId) {
        return getAppLaunchIntent(context, conversationId, (String) null);
    }

    public static Intent getAppLaunchIntent(@NonNull Context context) {
        return getAppLaunchIntent(context, new ArrayList<String>(), (String) null, EphemeralExpiration.NONE);
    }

    public static PendingIntent getNotificationAppLaunchIntent(@NonNull Context context) {
        return getNotificationAppLaunchIntent(context, null, (int) System.currentTimeMillis());
    }

    public static PendingIntent getNotificationAppLaunchIntent(@NonNull Context context, String conversationId, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_LAUNCH_FROM_NOTIFICATION, true);
        intent.putExtra(EXTRA_LAUNCH_START_CALL, false);
        intent.putStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS, new ArrayList<>(Collections.singletonList(conversationId)));
        return PendingIntent.getActivity(context, requestCode, intent, 0);
    }

    public static PendingIntent getNotificationCallIntent(@NonNull Context context, String conversationId, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_LAUNCH_FROM_NOTIFICATION, true);
        intent.putExtra(EXTRA_LAUNCH_START_CALL, true);
        intent.putStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS, new ArrayList<>(Collections.singletonList(conversationId)));
        return PendingIntent.getActivity(context, requestCode, intent, 0);
    }

    public static PendingIntent getNotificationReplyIntent(Context context, String conversationId, int requestCode) {
        Intent intent = new Intent(context, PopupActivity.class);
        intent.putExtra(EXTRA_LAUNCH_FROM_NOTIFICATION, true);
        intent.putStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS, new ArrayList<>(Collections.singletonList(conversationId)));
        return PendingIntent.getActivity(context, requestCode, intent, 0);
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
        String[] to = {"support@wire.com"};
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_EMAIL, to);
        intent.putExtra(Intent.EXTRA_STREAM, AndroidURIUtil.unwrap(fileUri));
        intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.debug_report__body));
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.debug_report__title, versionName));
        return intent;
    }

    public static String getLaunchConversationId(Intent intent) {
        List<String> convIds = intent.getStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS);
        return convIds == null || convIds.isEmpty() ? null : convIds.get(0);
    }

    public static List<String> getLaunchConversationIds(Intent intent) {
        return intent.getStringArrayListExtra(EXTRA_LAUNCH_CONVERSATION_IDS);
    }

    public static String getLaunchConversationSharedText(Intent intent) {
        return intent.getStringExtra(EXTRA_LAUNCH_CONVERSATION_MESSAGE);
    }

    private static List<Uri> getParcelableArrayListExtra(Intent intent) {
        List<Uri> files = intent.getParcelableArrayListExtra(EXTRA_LAUNCH_CONVERSATION_FILES);
        if (files == null) {
            return Collections.emptyList();
        } else {
            return files;
        }
    }

    public static List<URI> getLaunchConversationSharedFiles(Intent intent) {
        List<URI> androidUris = new ArrayList<>();
        for (Uri uri: getParcelableArrayListExtra(intent)) {
            androidUris.add(new AndroidURI(uri));
        }
        return androidUris;
    }

    public static EphemeralExpiration getEphemeralExpiration(Intent intent) {
        return EphemeralExpiration.getForMillis(intent.getLongExtra(EXTRA_LAUNCH_EPHEMERAL_EXPIRATION, EphemeralExpiration.NONE.milliseconds));
    }

    public static boolean isStartCallNotificationIntent(Intent intent) {
        return intent.getBooleanExtra(EXTRA_LAUNCH_START_CALL, false);
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
            return getGoogleMapsWebFallbackIntent(context, lat, lon, zoom, name);
        }
        return mapIntent;
    }

    private static Intent getGoogleMapsWebFallbackIntent(Context context, float lat, float lon, int zoom, String name) {
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
