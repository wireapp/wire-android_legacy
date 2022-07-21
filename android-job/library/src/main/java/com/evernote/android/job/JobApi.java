/*
 * Copyright (C) 2018 Evernote Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evernote.android.job;

import android.app.AlarmManager;
import android.app.Service;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.support.annotation.NonNull;

import com.evernote.android.job.v14.JobProxy14;
import com.evernote.android.job.v14.PlatformAlarmReceiver;
import com.evernote.android.job.v14.PlatformAlarmService;
import com.evernote.android.job.v14.PlatformAlarmServiceExact;
import com.evernote.android.job.v19.JobProxy19;
import com.evernote.android.job.v21.JobProxy21;
import com.evernote.android.job.v21.PlatformJobService;
import com.evernote.android.job.v24.JobProxy24;
import com.evernote.android.job.v26.JobProxy26;

import java.util.List;

/**
 * All available APIs.
 *
 * @author rwondratschek
 */
public enum JobApi {
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_26(true, false, true),
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_24(true, false, false),
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_21(true, true, false),
    /**
     * Uses the {@link AlarmManager} for scheduling jobs.
     */
    V_19(true, true, true),
    /**
     * Uses the {@link AlarmManager} for scheduling jobs.
     */
    V_14(false, true, true);

    private static final String JOB_SCHEDULER_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private volatile JobProxy mCachedProxy;

    private final boolean mSupportsExecutionWindow;
    private final boolean mFlexSupport;
    private final boolean mSupportsTransientJobs;

    JobApi(boolean supportsExecutionWindow, boolean flexSupport, boolean supportsTransientJobs) {
        mSupportsExecutionWindow = supportsExecutionWindow;
        mFlexSupport = flexSupport;
        mSupportsTransientJobs = supportsTransientJobs;
    }

    /*package*/ boolean supportsExecutionWindow() {
        return mSupportsExecutionWindow;
    }

    /*package*/ boolean isFlexSupport() {
        return mFlexSupport;
    }

    /*package*/ boolean supportsTransientJobs() {
        return mSupportsTransientJobs;
    }

    public boolean isSupported(Context context) {
        switch (this) {
            case V_26:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isServiceEnabled(context, PlatformJobService.class);
            case V_24:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isServiceEnabledAndHasPermission(context, PlatformJobService.class, JOB_SCHEDULER_PERMISSION);
            case V_21:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isServiceEnabledAndHasPermission(context, PlatformJobService.class, JOB_SCHEDULER_PERMISSION);
            case V_19:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isServiceEnabled(context, PlatformAlarmService.class)
                        && isBroadcastEnabled(context, PlatformAlarmReceiver.class);
            case V_14:
                return JobConfig.isForceAllowApi14()
                        || (isServiceEnabled(context, PlatformAlarmService.class) && isServiceEnabled(context, PlatformAlarmServiceExact.class)
                        && isBroadcastEnabled(context, PlatformAlarmReceiver.class));
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    @NonNull
    private JobProxy createProxy(Context context) {
        switch (this) {
            case V_26:
                return new JobProxy26(context);
            case V_24:
                return new JobProxy24(context);
            case V_21:
                return new JobProxy21(context);
            case V_19:
                return new JobProxy19(context);
            case V_14:
                return new JobProxy14(context);
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    @NonNull
    /*package*/ synchronized JobProxy getProxy(Context context) {
        if (mCachedProxy == null) {
            mCachedProxy = createProxy(context);
        }
        return mCachedProxy;
    }

    public synchronized void invalidateCachedProxy() {
        mCachedProxy = null;
    }

    private boolean isServiceEnabled(@NonNull Context context, @NonNull Class<? extends Service> clazz) {
        // on some rooted devices user can disable services
        try {
            Intent intent = new Intent(context, clazz);
            List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(intent, 0);
            return resolveInfos != null && !resolveInfos.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isServiceEnabledAndHasPermission(@NonNull Context context, @NonNull Class<? extends Service> clazz, @NonNull String permission) {
        try {
            Intent intent = new Intent(context, clazz);
            List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(intent, 0);
            if (resolveInfos == null || resolveInfos.isEmpty()) {
                return false;
            }

            for (ResolveInfo info : resolveInfos) {
                if (info.serviceInfo != null && permission.equals(info.serviceInfo.permission)) {
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBroadcastEnabled(@NonNull Context context, @NonNull Class<? extends BroadcastReceiver> clazz) {
        // on some rooted devices user can disable receivers
        try {
            Intent intent = new Intent(context, clazz);
            List<ResolveInfo> resolveInfos = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            return resolveInfos != null && !resolveInfos.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    public static JobApi getDefault(Context context) {
        if (V_26.isSupported(context) && JobConfig.isApiEnabled(V_26)) {
            return V_26;
        } else if (V_24.isSupported(context) && JobConfig.isApiEnabled(V_24)) {
            return V_24;
        } else if (V_21.isSupported(context) && JobConfig.isApiEnabled(V_21)) {
            return V_21;
        } else if (V_19.isSupported(context) && JobConfig.isApiEnabled(V_19)) {
            return V_19;
        } else if (JobConfig.isApiEnabled(V_14)) {
            return V_14;
        } else {
            throw new IllegalStateException("All supported APIs are disabled");
        }
    }
}
