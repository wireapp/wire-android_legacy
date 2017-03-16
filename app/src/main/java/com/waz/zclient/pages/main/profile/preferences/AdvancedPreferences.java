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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import com.localytics.android.Localytics;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.tracking.events.tracking.OptIn;
import com.waz.zclient.controllers.tracking.events.tracking.OptOut;
import com.waz.zclient.core.controllers.tracking.events.Event;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedImageDownloadPreferenceEvent;
import com.waz.zclient.pages.BasePreferenceFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.DebugUtils;
import timber.log.Timber;

public class AdvancedPreferences extends BasePreferenceFragment<AdvancedPreferences.Container> {

    private Preference debugReportPreference;

    public static AdvancedPreferences newInstance(String rootKey, Bundle extras) {
        AdvancedPreferences f = new AdvancedPreferences();
        Bundle args = extras == null ? new Bundle() : new Bundle(extras);
        args.putString(ARG_PREFERENCE_ROOT, rootKey);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreatePreferences2(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences2(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.preferences_advanced);
        debugReportPreference = findPreference(getString(R.string.pref_advanced_debug_report_key));
        if (debugReportPreference != null) {
            debugReportPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    DebugUtils.sendDebugReport(getActivity());
                    return true;
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        if (debugReportPreference != null) {
            debugReportPreference.setOnPreferenceClickListener(null);
            debugReportPreference = null;
        }
        super.onDestroyView();
    }

    @Override
    public Event handlePreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Event event = null;
        if (key.equals(getString(R.string.pref_options_image_download_key))) {
            String stringValue = sharedPreferences.getString(key, "");
            boolean wifiOnly = stringValue.equals(getContext().getString(R.string.zms_image_download_value_wifi));
            event = new ChangedImageDownloadPreferenceEvent(wifiOnly);
        } else if (key.equals(getString(R.string.pref_advanced_analytics_enabled_key))) {
            boolean enableTracking = sharedPreferences.getBoolean(key, false);
            if (enableTracking) {
                event = new OptIn();
                Localytics.setOptedOut(false);
            } else {
                try {
                    (((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class)).tagEvent(new OptOut());
                } catch (Exception e) {
                    Timber.e("Unable to tag event OptOut");
                    e.printStackTrace();
                }
                Localytics.setOptedOut(true);
            }
        }
        return event;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        getControllerFactory().getSpotifyController().handleActivityResult(requestCode,
                                                                           resultCode, data);
    }

    public interface Container {
    }
}
