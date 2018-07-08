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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;

public class WifiSetStatusReceiver extends BroadcastReceiver {
    private static final String COMMAND = "setstatus";
    private static final String COMMAND_ENABLE = "enable";
    private static final String COMMAND_DISABLE = "disable";

    // am broadcast -a com.wire.testinggallery.wifi --es setstatus [enable|disable]
    @Override
    public void onReceive(Context context, Intent intent) {
        String command = intent.getStringExtra(COMMAND);
        switch (command) {
            case COMMAND_ENABLE:
                setWifiStatus(context, true);
                break;
            case COMMAND_DISABLE:
                setWifiStatus(context, false);
                break;
            default:
                throw new RuntimeException(String.format("Cannot identify the command [%s]", command));
        }
        setResultCode(Activity.RESULT_OK);
    }

    private void setWifiStatus(Context context, boolean status) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(status);
    }

}
