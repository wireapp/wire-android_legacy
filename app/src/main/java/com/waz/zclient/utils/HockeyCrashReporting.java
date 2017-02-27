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

import android.app.Activity;
import android.content.Context;
import com.waz.threading.Threading;
import com.waz.zclient.controllers.tracking.events.exception.ExceptionEvent;
import com.waz.zclient.tracking.GlobalTrackingController;
import net.hockeyapp.android.Constants;
import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.CrashManagerListener;
import net.hockeyapp.android.NativeCrashManager;
import net.hockeyapp.android.UpdateManager;
import net.hockeyapp.android.utils.Util;
import timber.log.Timber;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.ref.WeakReference;

public class HockeyCrashReporting {

    public static void checkForCrashes(final Context context, final String deviceId, GlobalTrackingController trackingController) {
        Timber.v("checkForCrashes - registering...");

        final CrashManagerListener listener = new CrashManagerListener() {
            @Override
            public boolean shouldAutoUploadCrashes() {
                return true;
            }

            @Override
            public String getUserID() {
                return deviceId;
            }
        };

        CrashManager.initialize(context, Util.getAppIdentifier(context), listener);

        boolean nativeCrashFound = NativeCrashManager.loggedDumpFiles(Util.getAppIdentifier(context));
        if (nativeCrashFound) {
            StringBuffer details = new StringBuffer(Constants.PHONE_MANUFACTURER).append("/").append(Constants.PHONE_MODEL);
            trackingController.tagEvent(ExceptionEvent.exception("NDK", details.toString()));
        }

        // execute crash manager in background, it does IO and can take some time
        // XXX: this works because we use auto upload (and app context), so hockey doesn't try to show a dialog
        Threading.IO().execute(new Runnable() {
            @Override
            public void run() {
                // check number of crash reports, will drop them if there is too many
                String[] traces = new File(Constants.FILES_PATH).list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        return filename.endsWith(".stacktrace");
                    }
                });
                if (traces != null && traces.length > 256) {
                    Timber.v("checkForCrashes - found too many crash reports: %d, will drop them", traces.length);
                    CrashManager.deleteStackTraces(new WeakReference<>(context));
                }

                CrashManager.execute(context, listener);
            }
        });
    }

    public static void deleteCrashReports(final Context context) {
        Threading.IO().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    CrashManager.deleteStackTraces(new WeakReference<>(context));
                } catch (Throwable t) {
                    // ignore
                }
            }
        });
    }

    public static void checkForUpdates(Activity activity) {
        UpdateManager.register(activity);
    }

}
