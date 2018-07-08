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
package com.waz.zclient.utils.device;

import android.os.Build;

/**
 * Check out this list for more models:
 * https://github.com/mataanin/android-devices/blob/master/Build.Model%20-%20models.csv
 */
public class DeviceDetector {

    public static boolean isNexus6() {
        return isModel("Nexus 6");
    }

    public static boolean isGT_9500() {
        return isModel("GT-I9500");
    }

    public static boolean isSamsungTrend() {
        return isModel("GT-S7580") ||
               isModel("GT-S7570") ||
               isModel("GT-S7572");
    }

    public static boolean isNexus5() {
        return isModel("Nexus 5");
    }

    public static boolean isHTCDesire310() {
        return isModel("HTC Desire 310");
    }

    public static boolean isS5Mini() {
        return isModel("SM-G800F");
    }

    public static boolean isNexus7_2012() {
        return isModel("Nexus 7") &&
               isDevice("grouper");
    }

    public static boolean isVideoCallingEnabled() {
        return !(
            isHTCDesire310()
        );
    }

    private static boolean isModel(String model) {
        return model.equalsIgnoreCase(Build.MODEL);
    }

    private static boolean isDevice(String device) {
        return device.equalsIgnoreCase(Build.DEVICE);
    }
}
