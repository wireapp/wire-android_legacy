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
import android.widget.Toast;
import com.waz.api.MediaProvider;
import com.waz.zclient.BaseScalaActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.permission.RequestPermissionsObserver;
import com.waz.zclient.controllers.spotify.SpotifyObserver;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedImageDownloadPreferenceEvent;
import com.waz.zclient.core.controllers.tracking.events.Event;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedContactsPermissionEvent;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedSendButtonSettingEvent;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedThemeEvent;
import com.waz.zclient.media.SoundController;
import com.waz.zclient.pages.BasePreferenceFragment;
import com.waz.zclient.pages.main.profile.preferences.dialogs.WireRingtonePreferenceDialogFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.TrackingUtils;
import net.xpece.android.support.preference.RingtonePreference;
import net.xpece.android.support.preference.SwitchPreference;


public class OptionsPreferences extends BasePreferenceFragment<OptionsPreferences.Container> implements SharedPreferences.OnSharedPreferenceChangeListener,
                                                                                                        SpotifyObserver,
                                                                                                        RequestPermissionsObserver {

    private Preference.OnPreferenceChangeListener bindPreferenceSummaryToValueListener = new PreferenceSummaryChangeListener();
    private RingtonePreference ringtonePreference;
    private RingtonePreference textTonePreference;
    private RingtonePreference pingPreference;
    private Preference spotifyPreference;
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

        spotifyPreference = findPreference(getString(R.string.pref_options_spotify_key));
        if (getControllerFactory().getSpotifyController().isLoggedIn()) {
            spotifyPreference.setTitle(R.string.pref_options_spotify_logout_title);
            spotifyPreference.setSummary("");
        } else {
            spotifyPreference.setTitle(R.string.pref_options_spotify_title);
            spotifyPreference.setSummary(R.string.pref_options_spotify_summary);
        }
        spotifyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (getControllerFactory().getSpotifyController().isLoggedIn()) {
                    getControllerFactory().getSpotifyController().logout();
                } else {
                    getControllerFactory().getSpotifyController().login(getActivity());
                }
                return true;
            }
        });

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
        getControllerFactory().getSpotifyController().addSpotifyObserver(this);
        getControllerFactory().getRequestPermissionsController().addObserver(this);
    }

    @Override
    public void onStop() {
        getControllerFactory().getSpotifyController().removeSpotifyObserver(this);
        getControllerFactory().getRequestPermissionsController().removeObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        spotifyPreference.setOnPreferenceClickListener(null);
        spotifyPreference = null;
        super.onDestroyView();
    }

    @Override
    public void onLoginSuccess() {
        if (spotifyPreference == null) {
            return;
        }
        spotifyPreference.setTitle(R.string.pref_options_spotify_logout_title);
        spotifyPreference.setSummary("");
        getControllerFactory().getStreamMediaPlayerController()
                              .resetMediaPlayer(MediaProvider.SPOTIFY);
    }

    @Override
    public void onLoginFailed() {
        if (spotifyPreference == null) {
            return;
        }
        Toast.makeText(getActivity(), getString(R.string.pref_options_spotify_login_failed), Toast.LENGTH_SHORT)
             .show();
    }

    @Override
    public void onLogout() {
        if (spotifyPreference == null) {
            return;
        }
        spotifyPreference.setTitle(R.string.pref_options_spotify_title);
        spotifyPreference.setSummary(R.string.pref_options_spotify_summary);
        getControllerFactory().getStreamMediaPlayerController()
                              .resetMediaPlayer(MediaProvider.SPOTIFY);

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
            TrackingUtils.tagChangedSoundNotificationLevelEvent(((BaseScalaActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                                                stringValue,
                                                                getContext());

        } else if (key.equals(ringtonePreference.getKey()) ||
                   key.equals(textTonePreference.getKey()) ||
                   key.equals(pingPreference.getKey())) {

            SoundController ctrl = inject(SoundController.class);
            if (ctrl != null) {
                ctrl.setCustomSoundUrisFromPreferences(sharedPreferences);
            }
        } else if (key.equals(getString(R.string.pref_options_image_download_key))) {
            String stringValue = sharedPreferences.getString(key, "");
            boolean wifiOnly = stringValue.equals(getContext().getString(R.string.zms_image_download_value_wifi));
            event = new ChangedImageDownloadPreferenceEvent(wifiOnly);
        } else if (key.equals(getString(R.string.pref_options_contacts_key))) {
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
            boolean sendButtonIsOn = sharedPreferences.getBoolean(key, false);
            event = new ChangedSendButtonSettingEvent(sendButtonIsOn);
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

    public interface Container {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == PermissionUtils.REQUEST_READ_CONTACTS &&
            grantResults.length > 0 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            boolean oldConfig = getControllerFactory().getUserPreferencesController().hasShareContactsEnabled();
            getControllerFactory().getUserPreferencesController().setShareContactsEnabled(!oldConfig);
            getControllerFactory().getUserPreferencesController().setShareContactsEnabled(oldConfig);
        }
    }
}
