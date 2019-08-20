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

import android.app.Activity;
import android.content.Intent;
import com.waz.api.BugReporter;
import com.waz.api.ReportListener;
import com.waz.utils.wrappers.URI;

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

}
