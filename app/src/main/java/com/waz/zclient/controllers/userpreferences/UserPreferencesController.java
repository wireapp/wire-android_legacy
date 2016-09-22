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
import android.content.pm.PackageManager;
import com.waz.zclient.R;
import com.waz.zclient.camera.CameraFacing;
import timber.log.Timber;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UserPreferencesController implements IUserPreferencesController {

    public static final String USER_PREFS_TAG = "com.waz.zclient.user.preferences";
    private static final String USER_PREFS_VERSION_ID = "USER_PREFS_VERSION_ID";

    public static final String USER_PREFS_LAST_ACCENT_COLOR = "USER_PREFS_LAST_ACCENT_COLOR";
    public static final String USER_PREFS_REFERRAL_TOKEN = "USER_PREFS_REFERRAL_TOKEN";
    public static final String USER_PREFS_GENERIC_INVITATION_TOKEN = "USER_PREFS_GENERIC_INVITATION_TOKEN";
    public static final String USER_PREFS_PERSONAL_INVITATION_TOKEN = "USER_PREFS_PERSONAL_INVITATION_TOKEN";
    public static final String USER_PERFS_AB_TESTING_GROUP = "USER_PERFS_AB_TESTING_GROUP";
    private static final String USER_PREFS_SHOW_SHARE_CONTACTS_DIALOG = "USER_PREFS_SHOW_SHARE_CONTACTS_DIALOG ";
    private static final String USER_PREFS_RECENT_CAMERA_DIRECTION = "USER_PREFS_RECENT_CAMERA_DIRECTION";
    private static final String USER_PREF_SPOTIFY_LOGIN_COUNT = "PREF_SPOTIFY_LOGIN_COUNT";
    private static final String USER_PREF_PHONE_VERIFICATION_CODE = "PREF_PHONE_VERIFICATION_CODE";
    private static final String USER_PREF_APP_CRASH = "USER_PREF_APP_CRASH";
    private static final String USER_PREF_APP_CRASH_DETAILS = "USER_PREF_APP_CRASH_DETAILS";
    private static final String USER_PREF_FLASH_STATE = "USER_PREF_FLASH_STATE";
    private static final String USER_PREF_LOGGED_IN = "USER_PREF_LOGGED_IN_%s";
    private static final String USER_PREF_AB_TESTING_UUID = "USER_PREF_AB_TESTING_UUID";
    private static final String USER_PREF_ACTION_PREFIX = "USER_PREF_ACTION_PREFIX";
    private static final String USER_PREF_RECENT_EMOJIS = "USER_PREF_RECENT_EMOJIS";
    private static final String USER_PREF_UNSUPPORTED_EMOJIS = "USER_PREF_UNSUPPORTED_EMOJIS";
    private static final String USER_PREF_UNSUPPORTED_EMOJIS_CHECKED = "USER_PREF_UNSUPPORTED_EMOJIS_CHECKED";

    private static final String PREFS_DEVICE_ID = "com.waz.device.id";

    private static final int AB_TESTING_GROUP_COUNT = 6;

    private final SharedPreferences userPreferences;
    private Context context;

    public UserPreferencesController(Context context) {
        this.context = context;
        userPreferences = context.getSharedPreferences(USER_PREFS_TAG, Context.MODE_PRIVATE);

        // get old version
        int oldVersion = userPreferences.getInt(USER_PREFS_VERSION_ID, 0);
        int newVersion = 0;
        // get new version
        try {
            newVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "Failed loading version for UserPreferencesController!");
        }
        updateSharedPreferences(oldVersion, newVersion);
    }

    private void updateSharedPreferences(int oldVersion, int newVersion) {
        // TODO do something very clever here if old and new version do not match
        // at the end
        userPreferences.edit().putInt(USER_PREFS_VERSION_ID, newVersion).apply();
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
    public void setRecentCameraDirection(CameraFacing cameraFacing) {
        userPreferences.edit().putInt(USER_PREFS_RECENT_CAMERA_DIRECTION, cameraFacing.facing).apply();
    }

    @Override
    public CameraFacing getRecentCameraDirection() {
        return CameraFacing.getFacing(userPreferences.getInt(USER_PREFS_RECENT_CAMERA_DIRECTION,
                                                             CameraFacing.BACK.facing));
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
    public void setPersonalInvitationToken(String token) {
        userPreferences.edit().putString(USER_PREFS_PERSONAL_INVITATION_TOKEN, token).apply();
    }

    @Override
    public String getPersonalInvitationToken() {
        return userPreferences.getString(USER_PREFS_PERSONAL_INVITATION_TOKEN, null);
    }

    @Override
    public boolean showStatusBar() {
        return userPreferences.getBoolean(context.getString(R.string.pref_dev_status_bar_key), true);
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
    public void incrementSpotifyLoginTriesCount() {
        userPreferences.edit().putInt(USER_PREF_SPOTIFY_LOGIN_COUNT, getSpotifyLoginTriesCount() + 1).apply();
    }

    @Override
    public int getSpotifyLoginTriesCount() {
        return userPreferences.getInt(USER_PREF_SPOTIFY_LOGIN_COUNT, 0);
    }

    @Override
    public boolean isCursorSendButtonEnabled() {
        return userPreferences.getBoolean(context.getString(R.string.pref_options_cursor_send_button_key), true);
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
    @SuppressWarnings("CommitPrefEdits")
    public void setCrashException(String exception, String details) {
        userPreferences.edit()
                       .putString(USER_PREF_APP_CRASH, exception)
                       .putString(USER_PREF_APP_CRASH_DETAILS, details)
                       .commit();
    }

    @Override
    public String getCrashException() {
        String exception = userPreferences.getString(USER_PREF_APP_CRASH, null);
        if (exception != null) {
            userPreferences.edit().putString(USER_PREF_APP_CRASH, null).apply();
        }
        return exception;
    }

    @Override
    public String getCrashDetails() {
        String details = userPreferences.getString(USER_PREF_APP_CRASH_DETAILS, null);
        if (details != null) {
            userPreferences.edit().putString(USER_PREF_APP_CRASH_DETAILS, null).apply();
        }
        return details;
    }

    @Override
    public String getSavedFlashState() {
        return userPreferences.getString(USER_PREF_FLASH_STATE, "");
    }

    @Override
    public void setSavedFlashState(String state) {
        userPreferences.edit().putString(USER_PREF_FLASH_STATE, state).apply();
    }

    @Override
    public boolean isImageDownloadPolicyWifiOnly() {
        String downloadPolicyWifi = context.getString(R.string.zms_image_download_value_wifi);
        String defaultPolicy = context.getString(R.string.zms_image_download_value_always);
        String prefKey = context.getString(R.string.pref_options_image_download_key);
        return downloadPolicyWifi.equals(userPreferences.getString(prefKey, defaultPolicy));
    }

    @Override
    public boolean hasUserLoggedIn(String userId) {
        return userPreferences.getBoolean(String.format(USER_PREF_LOGGED_IN, userId), false);
    }

    @Override
    public void userLoggedIn(String userId) {
        userPreferences.edit().putBoolean(String.format(USER_PREF_LOGGED_IN, userId), true).apply();
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
    public int getABTestingGroup() {
        int group = userPreferences.getInt(USER_PERFS_AB_TESTING_GROUP, -1);
        if (group == -1) {
            UUID uuid = UUID.randomUUID();
            userPreferences.edit().putString(USER_PREF_AB_TESTING_UUID, uuid.toString()).apply();
            group = (int) Math.abs(uuid.getLeastSignificantBits() % AB_TESTING_GROUP_COUNT) + 1;
            userPreferences.edit().putInt(USER_PERFS_AB_TESTING_GROUP, group).apply();
        }
        return group;
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
    public void addUnsupportedEmoji(String emoji) {
        UnsupportedEmojis emojis = new UnsupportedEmojis(userPreferences.getString(USER_PREF_UNSUPPORTED_EMOJIS, null));
        if (emojis.addUnsupportedEmoji(emoji)) {
            userPreferences.edit().putString(USER_PREF_UNSUPPORTED_EMOJIS, emojis.getJson()).apply();
        }
    }

    @Override
    public Set<String> getUnsupportedEmojis() {
        return new UnsupportedEmojis(userPreferences.getString(USER_PREF_UNSUPPORTED_EMOJIS, null)).getUnsupportedEmojis();
    }

    @Override
    public boolean hasCheckedForUnsupportedEmojis(int version) {
        return userPreferences.getInt(USER_PREF_UNSUPPORTED_EMOJIS_CHECKED, 0) >= version;
    }

    @Override
    public void setCheckedForUnsupportedEmojis(int version) {
        userPreferences.edit().putInt(USER_PREF_UNSUPPORTED_EMOJIS_CHECKED, version).apply();
    }

}
