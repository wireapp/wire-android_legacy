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

import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import com.waz.api.CoreList;
import com.waz.api.InitListener;
import com.waz.api.OtrClient;
import com.waz.api.Self;
import com.waz.api.Subscriber;
import com.waz.api.Subscription;
import com.waz.api.UiSignal;
import com.waz.api.UpdateListener;
import com.waz.api.Verification;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.tracking.events.otr.ViewedOwnOtrClientsEvent;
import com.waz.zclient.pages.BasePreferenceFragment;
import com.waz.zclient.tracking.GlobalTrackingController;

public class DevicesPreferences extends BasePreferenceFragment<DevicesPreferences.Container> {

    public static final String TAG = DevicesPreferences.class.getName();

    private UiSignal<OtrClient> otrClient;
    private CoreList<OtrClient> otherClients;
    private final UpdateListener otrClientsUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            updateOtrDevices();
        }
    };
    private Subscription otrClientSubscription;

    public static DevicesPreferences newInstance(String rootKey, Bundle extras) {
        DevicesPreferences f = new DevicesPreferences();
        Bundle args = extras == null ? new Bundle() : new Bundle(extras);
        args.putString(ARG_PREFERENCE_ROOT, rootKey);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreatePreferences2(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences2(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.preferences_devices);
        getStoreFactory().getZMessagingApiStore().getApi().onInit(new InitListener() {
            @Override
            public void onInitialized(Self user) {
                setupOtrDevices();
            }
        });
        trackViewingOtrClients();
    }

    @Override
    public void onStart() {
        super.onStart();
        setupOtrDevices();
    }

    @Override
    public void onStop() {
        if (otherClients != null) {
            otherClients.removeUpdateListener(otrClientsUpdateListener);
            otherClients = null;
        }
        if (otrClientSubscription != null) {
            otrClientSubscription.cancel();
            otrClientSubscription = null;
        }
        otrClient = null;
        super.onStop();
    }

    private void trackViewingOtrClients() {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return;
        }
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ViewedOwnOtrClientsEvent());
    }

    private void setupOtrDevices() {
        if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
            return;
        }

        Self self = getStoreFactory().getZMessagingApiStore().getApi().getSelf();
        if (otrClient == null) {
            otrClient = self.getOtrClient();
        }
        if (otherClients == null) {
            otherClients = self.getOtherOtrClients();
            otherClients.addUpdateListener(otrClientsUpdateListener);
        }
        updateOtrDevices();

        final PreferenceGroup currentOtrClientPreferenceGroup = (PreferenceGroup) findPreference(getString(R.string.pref_devices_current_device_category_key));
        otrClientSubscription = otrClient.subscribe(new Subscriber<OtrClient>() {
            @Override
            public void next(OtrClient value) {
                if (getActivity() == null) {
                    return;
                }
                currentOtrClientPreferenceGroup.setTitle(getString(R.string.pref_devices_current_device_category_title));
                currentOtrClientPreferenceGroup.removeAll();

                net.xpece.android.support.preference.Preference preference = new net.xpece.android.support.preference.Preference(getContext());
                preference.setTitle(DevicesPreferencesUtil.getTitle(getActivity(), value));
                preference.setSummary(DevicesPreferencesUtil.getSummary(getActivity(), value, false));
                preference.setKey(getString(R.string.pref_device_details_screen_key));

                final PreferenceScreen preferenceScreen = new PreferenceScreen(getContext(), null);
                preferenceScreen.getExtras().putParcelable(DeviceDetailPreferences.PREFS_OTR_CLIENT, value);
                preferenceScreen.getExtras().putBoolean(DeviceDetailPreferences.PREFS_CURRENT_DEVICE, true);
                preferenceScreen.setTitle(preference.getTitle());
                preferenceScreen.setKey(preference.getKey());

                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        PreferenceManager preferenceManager = getPreferenceManager();
                        if (preferenceManager != null) {
                            PreferenceManager.OnNavigateToScreenListener listener = preferenceManager.getOnNavigateToScreenListener();
                            if (listener != null) {
                                listener.onNavigateToScreen(preferenceScreen);
                                return true;
                            }
                        }
                        return false;
                    }
                });
                currentOtrClientPreferenceGroup.addPreference(preference);
            }
        });
    }

    private void updateOtrDevices() {
        PreferenceGroup otherOtrClientPreferenceGroup = (PreferenceGroup) findPreference(getString(R.string.pref_devices_other_devices_category_key));
        if (otherOtrClientPreferenceGroup == null) {
            return;
        }
        otherOtrClientPreferenceGroup.removeAll();
        if (otherClients == null) {
            return;
        }

        for (int i = 0; i < otherClients.size(); i++) {
            OtrClient otrClient = otherClients.get(i);
            addClientToGroup(otrClient, otherOtrClientPreferenceGroup);
        }

        if (otherClients.size() > 0) {
            addDeviceWarning(otherOtrClientPreferenceGroup);
            otherOtrClientPreferenceGroup.setTitle(getString(R.string.pref_devices_other_devices_category_title));
        } else {
            otherOtrClientPreferenceGroup.setTitle("");
        }
    }

    private void addClientToGroup(final OtrClient otrClient, PreferenceGroup preferenceGroup) {
        DevicePreference preference = new DevicePreference(getContext());
        preference.setTitle(DevicesPreferencesUtil.getTitle(getActivity(), otrClient));
        preference.setKey(getString(R.string.pref_device_details_screen_key));
        preference.setSummary(DevicesPreferencesUtil.getSummary(getActivity(), otrClient, false));
        preference.setVerified(otrClient.getVerified() == Verification.VERIFIED);

        final PreferenceScreen preferenceScreen = new PreferenceScreen(getContext(), null);
        preferenceScreen.getExtras().putParcelable(DeviceDetailPreferences.PREFS_OTR_CLIENT, otrClient);
        preferenceScreen.setTitle(preference.getTitle());
        preferenceScreen.setKey(preference.getKey());

        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PreferenceManager preferenceManager = getPreferenceManager();
                if (preferenceManager != null) {
                    PreferenceManager.OnNavigateToScreenListener listener = preferenceManager.getOnNavigateToScreenListener();
                    if (listener != null) {
                        listener.onNavigateToScreen(preferenceScreen);
                        return true;
                    }
                }
                return false;
            }
        });
        preferenceGroup.addPreference(preference);
    }

    private void addDeviceWarning(PreferenceGroup preferenceGroup) {
        Preference preference = new Preference(getActivity());
        preference.setSummary(getString(R.string.pref_devices_warning_summary));
        preferenceGroup.addPreference(preference);
    }

    public interface Container {
    }
}
