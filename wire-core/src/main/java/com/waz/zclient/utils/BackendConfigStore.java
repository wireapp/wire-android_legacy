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

import android.content.Context;
import android.preference.PreferenceManager;

import com.waz.service.BackendConfig;

import java.util.NoSuchElementException;

import timber.log.Timber;

public class BackendConfigStore {
    private static final String CUSTOM_BACKEND_PREFERENCE = "custom_backend_pref";

    public static boolean isConfigSet(Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .contains(CUSTOM_BACKEND_PREFERENCE);
    }

    public static void setConfig(BackendConfig backend, Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(CUSTOM_BACKEND_PREFERENCE, backend.environment())
                .apply();
    }

    public static BackendConfig getConfig(Context context) {
        String backend = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(CUSTOM_BACKEND_PREFERENCE, null);
        if (backend == null) {
            return BackendConfig.ProdBackend();
        }
        try {
            return BackendConfig.byName().apply(backend);
        } catch (NoSuchElementException ex) {
            Timber.w("Could not find backend with name: %s. Defaulting to prod", backend);
            return BackendConfig.ProdBackend();
        }
    }
}
