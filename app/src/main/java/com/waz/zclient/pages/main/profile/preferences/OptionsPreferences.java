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
package com.waz.zclient.pages.main.profile.preferences;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.RawRes;
import android.support.v4.app.ActivityCompat;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.calling.controllers.CallPermissionsController;
import com.waz.zclient.controllers.permission.RequestPermissionsObserver;
import com.waz.zclient.core.controllers.tracking.events.Event;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedBitRateModeEvent;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedContactsPermissionEvent;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedImageDownloadPreferenceEvent;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedSendButtonSettingEvent;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedThemeEvent;
import com.waz.zclient.media.SoundController;
import com.waz.zclient.pages.main.profile.preferences.dialogs.WireRingtonePreferenceDialogFragment;
import com.waz.zclient.preferences.BasePreferenceFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.TrackingUtils;
import net.xpece.android.support.preference.RingtonePreference;
import net.xpece.android.support.preference.SwitchPreference;


public class OptionsPreferences extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener,
                                                                          RequestPermissionsObserver {

    private Preference.OnPreferenceChangeListener bindPreferenceSummaryToValueListener = new PreferenceSummaryChangeListener();
    private RingtonePreference ringtonePreference;
    private RingtonePreference textTonePreference;
    private RingtonePreference pingPreference;
    private SwitchPreference themePreference;

    public static OptionsPreferences newInstance(String rootKey, Bundle extras) {
        OptionsPreferences f = new OptionsPreferences();
        Bundle args = extras == null ? new Bundle() : new Bundle(extras);
        args.putString(ARG_PREFERENCE_ROOT, rootKey);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreatePreferences2(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences2(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.preferences_options);

        ringtonePreference = (RingtonePreference) findPreference(getString(R.string.pref_options_ringtones_ringtone_key));
        textTonePreference = (RingtonePreference) findPreference(getString(R.string.pref_options_ringtones_text_key));
        pingPreference = (RingtonePreference) findPreference(getString(R.string.pref_options_ringtones_ping_key));
        ringtonePreference.setShowSilent(true);
        textTonePreference.setShowSilent(true);
        pingPreference.setShowSilent(true);
        setDefaultRingtones();

        bindPreferenceSummaryToValue(ringtonePreference);
        bindPreferenceSummaryToValue(textTonePreference);
        bindPreferenceSummaryToValue(pingPreference);

        themePreference = (SwitchPreference) findPreference(getString(R.string.pref_options_theme_switch_key));
        themePreference.setChecked(getControllerFactory().getThemeController().isDarkTheme());

        if (LayoutSpec.isTablet(getActivity())) {
            PreferenceCategory requestedOptionsCategory = (PreferenceCategory) findPreference(getString(R.string.pref_options_requested_category_key));
            if (requestedOptionsCategory  != null) {
                requestedOptionsCategory.removePreference(themePreference);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getRequestPermissionsController().addObserver(this);
    }

    @Override
    public void onStop() {
        getControllerFactory().getRequestPermissionsController().removeObserver(this);
        super.onStop();
    }

    private void setDefaultRingtones() {
        addDefaultExtra(ringtonePreference.getKey(), R.raw.ringing_from_them);
        addDefaultExtra(textTonePreference.getKey(), R.raw.new_message);
        addDefaultExtra(pingPreference.getKey(), R.raw.ping_from_them);
    }

    private void addDefaultExtra(String key, @RawRes int defaultResId) {
        findPreference(key).getExtras().putInt(WireRingtonePreferenceDialogFragment.EXTRA_DEFAULT, defaultResId);
    }

    @Override
    public Event handlePreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Event event = null;
        if (key.equals(getString(R.string.pref_options_sounds_key))) {
            String stringValue = sharedPreferences.getString(key, "");
            TrackingUtils.tagChangedSoundNotificationLevelEvent(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                                                stringValue,
                                                                getContext());

        } else if (key.equals(ringtonePreference.getKey()) ||
                   key.equals(textTonePreference.getKey()) ||
                   key.equals(pingPreference.getKey())) {

            SoundController ctrl = injectJava(SoundController.class);
            if (ctrl != null) {
                ctrl.setCustomSoundUrisFromPreferences(sharedPreferences);
            }
        } else if (key.equals(getString(R.string.pref_options_image_download_key))) {
            String stringValue = sharedPreferences.getString(key, "");
            boolean wifiOnly = stringValue.equals(getContext().getString(R.string.zms_image_download_value_wifi));
            event = new ChangedImageDownloadPreferenceEvent(wifiOnly);
        } else if (key.equals(getString(R.string.pref_share_contacts_key))) {
            boolean shareContacts = sharedPreferences.getBoolean(key, false);
            event = new ChangedContactsPermissionEvent(shareContacts, true);
            boolean hasContactsReadPermission = PermissionUtils.hasSelfPermissions(getContext(), Manifest.permission.READ_CONTACTS);
            if (shareContacts && !hasContactsReadPermission) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.READ_CONTACTS}, PermissionUtils.REQUEST_READ_CONTACTS);
            }
        } else if (key.equals(getString(R.string.pref_options_theme_switch_key))) {
            getControllerFactory().getThemeController().toggleThemePending(true);
            event = new ChangedThemeEvent(getControllerFactory().getThemeController().isDarkTheme());
        } else if (key.equals(getString(R.string.pref_options_cursor_send_button_key))) {
            boolean sendButtonIsOn = sharedPreferences.getBoolean(key, true);
            event = new ChangedSendButtonSettingEvent(sendButtonIsOn);
        } else if (key.equals(getString(R.string.pref_options_vbr_key))) {
            boolean vbrOn = sharedPreferences.getBoolean(key, false);
            CallPermissionsController ctrl = injectJava(CallPermissionsController.class);
            if (ctrl != null) {
                ctrl.setVariableBitRateMode(vbrOn);
            }
            event = new ChangedBitRateModeEvent(vbrOn, true);
        }
        return event;
    }

    private void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(bindPreferenceSummaryToValueListener);
        final String key = preference.getKey();
        String value = getPreferenceManager().getSharedPreferences().getString(key, null);
        bindPreferenceSummaryToValueListener.onPreferenceChange(preference, value);
    }

    private static class PreferenceSummaryChangeListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            final String value = (String) o;
            if (!(preference instanceof RingtonePreference)) {
                preference.setSummary(value);
                return true;
            }

            final Context context = preference.getContext();
            if (value == null) {
                preference.setSummary(R.string.pref_options_ringtones_default_summary);
                return true;
            } else if (value.isEmpty()) {
                preference.setSummary(RingtonePreference.getRingtoneSilentString(context));
                return true;
            }

            final Uri uri = Uri.parse(value);
            final int rawId = preference.getExtras().getInt(WireRingtonePreferenceDialogFragment.EXTRA_DEFAULT);
            if (uri.compareTo(Uri.parse("android.resource://" + context.getPackageName() + "/" + rawId)) == 0) {
                preference.setSummary(R.string.pref_options_ringtones_default_summary);
                return true;
            }

            preference.setSummary(RingtonePreference.getRingtoneTitle(context, uri));
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == PermissionUtils.REQUEST_READ_CONTACTS) {
            getControllerFactory().getUserPreferencesController().setShareContactsEnabled(false);
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //Changing the value of the shareContacts seems to be the
                //only way to trigger a refresh on the sync engine...
                getControllerFactory().getUserPreferencesController().setShareContactsEnabled(true);
            }
        }
    }
}
