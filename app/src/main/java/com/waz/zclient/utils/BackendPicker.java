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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import com.waz.service.BackendConfig;
import com.waz.service.ZMessaging;
import com.waz.zclient.BuildConfig;
import timber.log.Timber;

import java.util.NoSuchElementException;

public class BackendPicker {

    private static final String CUSTOM_BACKEND_PREFERENCE = "custom_backend_pref";
    private final Context context;

    private final String[] backends = new String[] {
            BackendConfig.StagingBackend().environment(),
            BackendConfig.ProdBackend().environment()
    };

    public BackendPicker(Context context) {
        this.context = context;
    }

    public void withBackend(Activity activity, final Callback<Void> callback) {
        if (shouldShowBackendPicker()) {
            showDialog(activity, callback);
        } else {
            callback.callback(null);
        }
    }

    public void withBackend(final Callback<Void> callback) {
        BackendConfig be = getBackendConfig();
        if (be != null) {
            ZMessaging.useBackend(be);
            callback.callback(null);
        }
    }

    private void showDialog(Activity activity, final Callback<Void> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Select Backend");
        builder.setItems(backends, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BackendConfig be = BackendConfig.byName().apply(backends[which]);
                ZMessaging.useBackend(be);
                saveBackendConfig(be);
                callback.callback(null);
            }
        });
        builder.setCancelable(false);
        builder.create().show();
    }

    private boolean shouldShowBackendPicker() {
        if (!BuildConfig.DEVELOPER_FEATURES_ENABLED) {
            return false;
        }
        return !PreferenceManager.getDefaultSharedPreferences(context).contains(CUSTOM_BACKEND_PREFERENCE);
    }

    @Nullable
    private BackendConfig getBackendConfig() {
        return BuildConfig.DEVELOPER_FEATURES_ENABLED ? getCustomBackend() : BackendConfig.ProdBackend();
    }

    @SuppressLint("CommitPrefEdits") // lint not seeing commit
    private void saveBackendConfig(BackendConfig backend) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(CUSTOM_BACKEND_PREFERENCE, backend.environment()).commit();
    }

    @Nullable
    private BackendConfig getCustomBackend() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String backend = prefs.getString(CUSTOM_BACKEND_PREFERENCE, null);
        if (backend != null) {
            try {
                return BackendConfig.byName().apply(backend);
            } catch (NoSuchElementException ex) {
                Timber.w("Could not find backend with name: %s", backend);
            }
        }
        return null;
    }
}

