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
package com.waz.zclient.controllers.deviceuser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.telephony.TelephonyManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceUserController implements IDeviceUserController {

    private Context context;
    private String primaryPhoneNumber;
    private String countryCode;

    public DeviceUserController(Context context) {
        this.context = context;
    }

    @Override
    public void tearDown() {
        context = null;
    }

    @Override
    public String getPhoneNumber(String countryCode) {
        if (primaryPhoneNumber == null) {
            fetchSIMPhoneNumber();
            cleanPhoneNumber();
        }

        if (countryCode != null && primaryPhoneNumber != null &&
            primaryPhoneNumber.length() > countryCode.length() && primaryPhoneNumber.startsWith(countryCode)) {
            return primaryPhoneNumber.substring(countryCode.length());
        }

        return primaryPhoneNumber;
    }

    @Override
    public String getPhoneCountryISO() {
        if (countryCode == null) {
            fetchSIMPhoneNumber();
            cleanPhoneNumber();
        }

        return countryCode;
    }

    private void cleanPhoneNumber() {
        if (primaryPhoneNumber == null) {
            return;
        }
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(primaryPhoneNumber);
        StringBuilder builder = new StringBuilder();
        while (m.find()) {
            builder.append(m.group());
        }
        primaryPhoneNumber = builder.toString();
    }

    @SuppressLint("HardwareIds")
    private void fetchSIMPhoneNumber() {
        if (primaryPhoneNumber == null) {
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                primaryPhoneNumber = tm.getLine1Number();
                countryCode = tm.getSimCountryIso();
                if (countryCode == null) {
                    countryCode = tm.getNetworkCountryIso();
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

}
