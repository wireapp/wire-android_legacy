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
package com.waz.zclient.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.waz.zclient.controllers.userpreferences.UserPreferencesController;
import timber.log.Timber;

public class ReferralBroadcastReceiver extends BroadcastReceiver {

    private static final String PERSONAL_INVITE_PREFIX = "invite-";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent.hasExtra("referrer")) {
                String token = intent.getStringExtra("referrer");
                if (token != null) {
                    SharedPreferences prefs = context.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE);
                    if (token.startsWith(PERSONAL_INVITE_PREFIX)) {
                        prefs.edit().putString(UserPreferencesController.USER_PREFS_PERSONAL_INVITATION_TOKEN,
                                               token.substring(PERSONAL_INVITE_PREFIX.length())).apply();
                    } else {
                        prefs.edit().putString(UserPreferencesController.USER_PREFS_REFERRAL_TOKEN, token).apply();
                    }
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Unable to get referral token");
        }
    }
}
