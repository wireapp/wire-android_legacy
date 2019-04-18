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
import com.waz.zclient.BuildConfig;
import com.waz.zclient.Backend;

import timber.log.Timber;

import java.util.NoSuchElementException;

public class BackendPicker {

    public static final String CUSTOM_BACKEND_PREFERENCE = "custom_backend_pref";
    private final Context context;

    public BackendPicker(Context context) {
        this.context = context;
    }

    public void withBackend(Activity activity, final Callback<BackendConfig> callback, BackendConfig prodBackend) {
        if (shouldShowBackendPicker()) {
            showDialog(prodBackend, activity, callback);
        } else {
            callback.callback(getBackendConfig(prodBackend));
        }
    }

    public void withBackend(final Callback<BackendConfig> callback, BackendConfig prodBackend) {
        BackendConfig be = getBackendConfig(prodBackend);
        if (be != null) {
            callback.callback(be);
        }
    }

    private void showDialog(final BackendConfig prod, Activity activity, final Callback<BackendConfig> callback) {
        final String[] backends = new String[] {
            Backend.StagingBackend().getEnvironment(),
            prod.getEnvironment()
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Select Backend");
        builder.setItems(backends, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BackendConfig be = Backend.byName().apply(backends[which]);
                saveBackendConfig(be);
                callback.callback(be);
            }
        });
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();

        SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(CUSTOM_BACKEND_PREFERENCE)) {
                    BackendConfig be = getBackendConfig(prod);
                    if (be != null) {
                        callback.callback(be);
                        prefs().unregisterOnSharedPreferenceChangeListener(this);
                    }
                }
            }
        };

        prefs().registerOnSharedPreferenceChangeListener(listener);
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private boolean shouldShowBackendPicker() {
        if (!BuildConfig.DEVELOPER_FEATURES_ENABLED) {
            return false;
        }
        return !prefs().contains(CUSTOM_BACKEND_PREFERENCE);
    }

    @Nullable
    private BackendConfig getBackendConfig(BackendConfig prodBackend) {
        return BuildConfig.DEVELOPER_FEATURES_ENABLED ? getCustomBackend() : prodBackend;
    }

    @SuppressLint("CommitPrefEdits") // lint not seeing commit
    private void saveBackendConfig(BackendConfig backend) {
        prefs()
            .edit()
            .putString(CUSTOM_BACKEND_PREFERENCE, backend.getEnvironment())
            .commit();
    }

    @Nullable
    private BackendConfig getCustomBackend() {
        String backend = prefs().getString(CUSTOM_BACKEND_PREFERENCE, null);
        if (backend != null) {
            try {
                return Backend.byName().apply(backend);
            } catch (NoSuchElementException ex) {
                Timber.w("Could not find backend with name: %s", backend);
            }
        }
        return null;
    }
}

