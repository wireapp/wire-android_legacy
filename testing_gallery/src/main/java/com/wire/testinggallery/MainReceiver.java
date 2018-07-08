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
package com.wire.testinggallery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

public class MainReceiver extends BroadcastReceiver {
    private static final String COMMAND = "command";
    private static final String PACKAGE_NAME = "package";
    private static final String CUSTOM_TEXT = "text";
    private static final String COMMAND_SHARE_TEXT = "share_text";
    private static final String COMMAND_CHECK_NOTIFICATION_ACCESS = "check_notification_access";
    private static final String DEFAULT_TEST_TEXT = "QA AUTOMATION TEST";
    private static final String DEFAULT_PACKAGE_NAME = "com.wire.candidate";
    private static final String COMMAND_SHARE_IMAGE = "share_image";
    private static final String COMMAND_SHARE_VIDEO = "share_video";
    private static final String COMMAND_SHARE_AUDIO = "share_audio";
    private static final String COMMAND_SHARE_FILE = "share_file";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context applicationContext = context.getApplicationContext();
        Intent shareIntent;
        String command = intent.getStringExtra(COMMAND);
        String packageName = intent.getStringExtra(PACKAGE_NAME) == null ?
            DEFAULT_PACKAGE_NAME : intent.getStringExtra(PACKAGE_NAME);
        if (command.startsWith("share")) {
            if (command.equals(COMMAND_SHARE_TEXT)) {
                String text = intent.getStringExtra(CUSTOM_TEXT);
                if (text == null) {
                    text = DEFAULT_TEST_TEXT;
                }
                shareIntent = getTextIntent(text);
            } else {
                Uri uri = getLatestAssetUriByCommand(applicationContext, command);
                shareIntent = getStreamIntent(applicationContext, uri);
            }
            shareIntent.setPackage(packageName);
            applicationContext.startActivity(shareIntent);
        } else {
            switch (command) {
                case COMMAND_CHECK_NOTIFICATION_ACCESS:
                    if (Settings.Secure.getString(applicationContext.getContentResolver(), "enabled_notification_listeners").contains(applicationContext.getPackageName())) {
                        setResultData("VERIFIED");
                    } else {
                        setResultData("UNVERIFIED");
                    }
                    break;
                default:
                    throw new RuntimeException(String.format("Cannot identify your command [%s]", command));
            }
        }
    }

    private Intent getStreamIntent(Context applicationContext, Uri uri) {
        Intent intent = getShareIntent();
        intent.setType(applicationContext.getContentResolver().getType(uri));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        return intent;
    }

    private Intent getTextIntent(String text) {
        Intent intent = getShareIntent();
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        return intent;
    }

    private Intent getShareIntent() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.addCategory(Intent.CATEGORY_DEFAULT);
        return shareIntent;
    }

    private Uri getLatestAssetUriByCommand(Context applicationContext, String command) {
        DocumentResolver resolver = new DocumentResolver(applicationContext.getContentResolver());
        switch (command) {
            case COMMAND_SHARE_FILE:
                return resolver.getDocumentUri();
            case COMMAND_SHARE_IMAGE:
                return resolver.getImageUri();
            case COMMAND_SHARE_VIDEO:
                return resolver.getVideoUri();
            case COMMAND_SHARE_AUDIO:
                return resolver.getAudioUri();
            default:
                throw new RuntimeException(String.format("Cannot identify the command : %s", command));
        }
    }
}
