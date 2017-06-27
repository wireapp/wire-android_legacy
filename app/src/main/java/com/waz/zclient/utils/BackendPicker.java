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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.waz.service.BackendConfig;
import com.waz.service.ZMessaging;
import com.waz.zclient.BuildConfig;

public class BackendPicker {
    private final Context context;

    private final String[] backends = new String[]{
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
        if (!BackendConfigStore.isConfigSet(context)) {
            return;
        }
        ZMessaging.useBackend(getBackendConfig());
        callback.callback(null);
    }

    private void showDialog(Activity activity, final Callback<Void> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Select Backend");
        builder.setItems(backends, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BackendConfig be = BackendConfig.byName().apply(backends[which]);
                ZMessaging.useBackend(be);
                BackendConfigStore.setConfig(be, context);
                callback.callback(null);
            }
        });
        builder.setCancelable(false);
        builder.create().show();
    }

    private boolean shouldShowBackendPicker() {
        return BuildConfig.SHOW_BACKEND_PICKER && !BackendConfigStore.isConfigSet(context);
    }

    private BackendConfig getBackendConfig() {
        return BuildConfig.SHOW_BACKEND_PICKER ?
            BackendConfigStore.getConfig(context) : BackendConfig.ProdBackend();
    }
}

