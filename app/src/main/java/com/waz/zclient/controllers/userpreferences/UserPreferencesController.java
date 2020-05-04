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
package com.waz.zclient.controllers.userpreferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.waz.zclient.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserPreferencesController implements IUserPreferencesController {

    public static final String USER_PREFS_TAG = "com.wire.preferences";

    //TODO Move these preferences to the UserPreferences service in SE, since a lot of them are user-scoped anyway.
    public static final String USER_PREFS_REFERRAL_TOKEN = "USER_PREFS_REFERRAL_TOKEN";
    public static final String USER_PREFS_PERSONAL_INVITATION_TOKEN = "USER_PREFS_PERSONAL_INVITATION_TOKEN";
    private static final String USER_PREF_PHONE_VERIFICATION_CODE = "PREF_PHONE_VERIFICATION_CODE";
    public static final String USER_PREF_ACTION_PREFIX = "USER_PREF_ACTION_PREFIX";
    private static final String USER_PREF_RECENT_EMOJIS = "USER_PREF_RECENT_EMOJIS";
    private static final String USER_PREF_UNSUPPORTED_EMOJIS = "USER_PREF_UNSUPPORTED_EMOJIS";
    private static final String USER_PREF_UNSUPPORTED_EMOJIS_CHECKED = "USER_PREF_UNSUPPORTED_EMOJIS_CHECKED";

    private final SharedPreferences userPreferences;

    public UserPreferencesController(Context context) {
        userPreferences = context.getSharedPreferences(USER_PREFS_TAG, Context.MODE_PRIVATE);
    }

    @Override
    public void tearDown() {
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void reset() {
        // TODO: AN-2066 Should reset all preferences
    }

    @Override
    public void setVerificationCode(String code) {
        userPreferences.edit().putString(USER_PREF_PHONE_VERIFICATION_CODE, code).apply();
    }

    @Override
    public void removeVerificationCode() {
        userPreferences.edit().remove(USER_PREF_PHONE_VERIFICATION_CODE).apply();
    }

    @Override
    public String getVerificationCode() {
        return userPreferences.getString(USER_PREF_PHONE_VERIFICATION_CODE, null);
    }

    @Override
    public boolean hasVerificationCode() {
        return userPreferences.contains(USER_PREF_PHONE_VERIFICATION_CODE);
    }

    @Override
    public void setPerformedAction(@Action int action) {
        userPreferences.edit().putBoolean(USER_PREF_ACTION_PREFIX + action, true).apply();
    }

    @Override
    public boolean hasPerformedAction(@Action int action) {
        return userPreferences.getBoolean(USER_PREF_ACTION_PREFIX + action, false);
    }

    @Override
    public void addRecentEmoji(String emoji) {
        RecentEmojis recentEmojis = new RecentEmojis(userPreferences.getString(USER_PREF_RECENT_EMOJIS, null));
        recentEmojis.addRecentEmoji(emoji);
        userPreferences.edit().putString(USER_PREF_RECENT_EMOJIS, recentEmojis.getJson()).apply();
    }

    @Override
    public List<String> getRecentEmojis() {
        return new RecentEmojis(userPreferences.getString(USER_PREF_RECENT_EMOJIS, null)).getRecentEmojis();
    }

    @Override
    public void setUnsupportedEmoji(Collection<String> emoji, int version) {
        JSONArray array = new JSONArray();
        for (String e : emoji) {
            array.put(e);
        }
        userPreferences.edit()
            .putString(USER_PREF_UNSUPPORTED_EMOJIS, array.toString())
            .putInt(USER_PREF_UNSUPPORTED_EMOJIS_CHECKED, version)
            .apply();
    }

    @Override
    public Set<String> getUnsupportedEmojis() {
        String json = userPreferences.getString(USER_PREF_UNSUPPORTED_EMOJIS, null);
        Set<String> unsupportedEmojis = new HashSet<>();
        if (!StringUtils.isBlank(json)) {
            try {
                JSONArray jsonArray = new JSONArray(json);
                for (int i = 0; i < jsonArray.length(); i++) {
                    unsupportedEmojis.add(jsonArray.getString(i));
                }
            } catch (JSONException e) {
                // ignore
            }
        }
        return unsupportedEmojis;
    }

    @Override
    public boolean hasCheckedForUnsupportedEmojis(int version) {
        return userPreferences.getInt(USER_PREF_UNSUPPORTED_EMOJIS_CHECKED, 0) >= version;
    }
}
