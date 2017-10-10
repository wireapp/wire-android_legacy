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
package com.waz.zclient.controllers.userpreferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import com.waz.zclient.R;
import com.waz.zclient.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UserPreferencesController implements IUserPreferencesController {

    public static final String USER_PREFS_TAG = "com.wire.preferences";

    //TODO Move these preferences to the UserPreferences service in SE, since a lot of them are user-scoped anyway.
    public static final String USER_PREFS_LAST_ACCENT_COLOR = "USER_PREFS_LAST_ACCENT_COLOR";
    public static final String USER_PREFS_REFERRAL_TOKEN = "USER_PREFS_REFERRAL_TOKEN";
    public static final String USER_PREFS_GENERIC_INVITATION_TOKEN = "USER_PREFS_GENERIC_INVITATION_TOKEN";
    public static final String USER_PREFS_PERSONAL_INVITATION_TOKEN = "USER_PREFS_PERSONAL_INVITATION_TOKEN";
    private static final String USER_PREFS_SHOW_SHARE_CONTACTS_DIALOG = "USER_PREFS_SHOW_SHARE_CONTACTS_DIALOG ";
    private static final String USER_PREF_PHONE_VERIFICATION_CODE = "PREF_PHONE_VERIFICATION_CODE";
    public static final String USER_PREF_ACTION_PREFIX = "USER_PREF_ACTION_PREFIX";
    private static final String USER_PREF_RECENT_EMOJIS = "USER_PREF_RECENT_EMOJIS";
    private static final String USER_PREF_UNSUPPORTED_EMOJIS = "USER_PREF_UNSUPPORTED_EMOJIS";
    private static final String USER_PREF_UNSUPPORTED_EMOJIS_CHECKED = "USER_PREF_UNSUPPORTED_EMOJIS_CHECKED";
    private static final String USER_PREF_LAST_EPHEMERAL_VALUE = "USER_PREF_LAST_EPHEMERAL_VALUE";

    private static final String PREFS_DEVICE_ID = "com.waz.device.id";

    private final SharedPreferences userPreferences;
    private Context context;

    public UserPreferencesController(Context context) {
        userPreferences = context.getSharedPreferences(USER_PREFS_TAG, Context.MODE_PRIVATE);
        this.context = context;
    }

    @Override
    public void tearDown() {
        context = null;
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void reset() {
        // TODO: AN-2066 Should reset all preferences
    }

    public void setLastAccentColor(int accentColor) {
        userPreferences.edit().putInt(USER_PREFS_LAST_ACCENT_COLOR, accentColor).apply();
    }

    public int getLastAccentColor() {
        return userPreferences.getInt(USER_PREFS_LAST_ACCENT_COLOR, -1);
    }

    @Override
    public boolean showContactsDialog() {
        return userPreferences.getBoolean(USER_PREFS_SHOW_SHARE_CONTACTS_DIALOG, true);
    }

    @Override
    public void setReferralToken(String token) {
        userPreferences.edit().putString(USER_PREFS_REFERRAL_TOKEN, token).apply();
    }

    @Override
    public String getReferralToken() {
        return userPreferences.getString(USER_PREFS_REFERRAL_TOKEN, null);
    }

    @Override
    public void setGenericInvitationToken(String token) {
        userPreferences.edit().putString(USER_PREFS_GENERIC_INVITATION_TOKEN, token).apply();
    }

    @Override
    public String getGenericInvitationToken() {
        return userPreferences.getString(USER_PREFS_GENERIC_INVITATION_TOKEN, null);
    }

    @Override
    public String getLastCallSessionId() {
        return userPreferences.getString(context.getString(R.string.pref_dev_avs_last_call_session_id_key),
                                         context.getString(R.string.pref_dev_avs_last_call_session_id_not_available));
    }

    @Override
    public void setPostSessionIdToConversation(boolean postSessionIdToConversation) {
        userPreferences.edit().putBoolean(context.getString(R.string.pref_dev_avs_post_session_id_key), postSessionIdToConversation).apply();
    }

    @Override
    public boolean isPostSessionIdToConversation() {
        return userPreferences.getBoolean(context.getString(R.string.pref_dev_avs_post_session_id_key), false);
    }

    @Override
    public String getDeviceId() {
        String id = userPreferences.getString(PREFS_DEVICE_ID, null);
        if (id == null) {
            id = getLegacyDeviceId();
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            userPreferences.edit()
                           .putString(PREFS_DEVICE_ID, id)
                           .apply();
        }
        return id;
    }

    private String getLegacyDeviceId() {
        SharedPreferences prefs = context.getSharedPreferences("zprefs", Context.MODE_PRIVATE);
        return prefs.getString(PREFS_DEVICE_ID, null);
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

    @Override
    public long getLastEphemeralValue() {
        return userPreferences.getLong(USER_PREF_LAST_EPHEMERAL_VALUE, 0);
    }

    @Override
    public void setLastEphemeralValue(long value) {
        userPreferences.edit().putLong(USER_PREF_LAST_EPHEMERAL_VALUE, value).apply();
    }

    @Override
    public boolean isVariableBitRateEnabled() {
        return userPreferences.getBoolean(context.getString(R.string.pref_options_vbr_key), true);
    }
}
