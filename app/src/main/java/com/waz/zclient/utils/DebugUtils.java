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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import com.waz.api.BugReporter;
import com.waz.api.ReportListener;
import com.waz.api.ZmsVersion;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.R;
import timber.log.Timber;

import java.util.Locale;

public class DebugUtils {

    public static void sendDebugReport(final Activity activity) {
        BugReporter.generateReport(new ReportListener() {
            @Override
            public void onReportGenerated(URI fileUri) {
                if (activity != null) {
                    Intent debugReportIntent = IntentUtils.getDebugReportIntent(activity, fileUri);
                    activity.startActivityForResult(Intent.createChooser(debugReportIntent, "Send debug report via..."), 12341);
                }
            }
        });
    }

    @SuppressLint("DefaultLocale")
    public static String getVersion(Context context) {
        final StringBuilder versionText = new StringBuilder();
        try {
            final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionText.append(String.format("Version %s (%d)", packageInfo.versionName, packageInfo.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "Failed getting app version name.");
        }
        versionText.append("\nSync Engine ").append(ZmsVersion.ZMS_VERSION)
                   .append("\nAVS ").append(context.getString(R.string.avs_version))
                   .append("\nAudio-notifications ").append(context.getString(R.string.audio_notifications_version))
                   .append("\nTranslations ").append(context.getString(R.string.wiretranslations_version))
                   .append("\nLocale ").append(getLocale(context));

        return versionText.toString();
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Locale getLocale(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return context.getResources().getConfiguration().getLocales().get(0);
            } else {
                //noinspection deprecation
                return context.getResources().getConfiguration().locale;
            }
        } catch (Exception e) {
            return null;
        }
    }

}
