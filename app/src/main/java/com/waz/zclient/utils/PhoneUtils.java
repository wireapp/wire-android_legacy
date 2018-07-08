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

import android.content.Context;
import android.telephony.TelephonyManager;

public class PhoneUtils {

    public enum PhoneState {

        IDLE,
        OFF_HOOK,
        RINGING

    }

    public static PhoneState getPhoneState(Context context) {
        int state = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).getCallState();
        return getPhoneStateFromCallState(state);
    }

    private static PhoneState getPhoneStateFromCallState(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                return PhoneState.IDLE;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                return PhoneState.OFF_HOOK;
            case TelephonyManager.CALL_STATE_RINGING:
                return PhoneState.RINGING;
        }
        return PhoneState.IDLE;
    }
}
