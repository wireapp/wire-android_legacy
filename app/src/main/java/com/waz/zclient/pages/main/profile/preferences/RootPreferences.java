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

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import com.waz.api.CoreList;
import com.waz.api.InitListener;
import com.waz.api.OtrClient;
import com.waz.api.Self;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.pages.BasePreferenceFragment;
import com.waz.zclient.pages.main.profile.ZetaPreferencesActivity;
import com.waz.zclient.pages.main.profile.preferences.dialogs.AvsOptionsDialogFragment;

public class RootPreferences extends BasePreferenceFragment<RootPreferences.Container> implements AvsOptionsDialogFragment.Container,
                                                                                                  AccentColorObserver {

    public static final String TAG = RootPreferences.class.getSimpleName();
    private BadgeablePreferenceScreenLike devicesPreferenceScreenLike;

    private CoreList<OtrClient> otrClients;
    private final ModelObserver<CoreList<OtrClient>> otrClientsObserver = new ModelObserver<CoreList<OtrClient>>() {
        @Override
        public void updated(CoreList<OtrClient> model) {
            if (devicesPreferenceScreenLike == null ||
                getStoreFactory() == null ||
                getStoreFactory().isTornDown()) {
                return;
            }
            otrClients = model;
            devicesPreferenceScreenLike.setBadgeCount(model.size());
        }
    };

    public static RootPreferences newInstance(String rootKey, Bundle extras) {
        RootPreferences f = new RootPreferences();
        Bundle args = extras == null ? new Bundle() : new Bundle(extras);
        args.putString(ARG_PREFERENCE_ROOT, rootKey);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreatePreferences2(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences2(savedInstanceState, rootKey);
        if (BuildConfig.SHOW_DEVELOPER_OPTIONS) {
            addPreferencesFromResource(R.xml.preference_root__developer);
            findPreference(getString(R.string.pref_dev_avs_screen_key)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    getChildFragmentManager().beginTransaction()
                                             .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                             .add(AvsOptionsDialogFragment.newInstance(), AvsOptionsDialogFragment.TAG)
                                             .addToBackStack(AvsOptionsDialogFragment.TAG)
                                             .commit();
                    return true;
                }
            });
        } else {
            addPreferencesFromResource(R.xml.preference_root);
        }

        devicesPreferenceScreenLike = (BadgeablePreferenceScreenLike) findPreference(getString(R.string.pref_devices_screen_key));
        devicesPreferenceScreenLike.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (otrClients != null) {
                    for (OtrClient client : otrClients) {
                        client.setVerified(false);
                    }
                }
                return false;
            }
        });

        getStoreFactory().getZMessagingApiStore().getApi().onInit(new InitListener() {
            @Override
            public void onInitialized(Self user) {
                otrClientsObserver.setAndUpdate(getStoreFactory().getZMessagingApiStore().getApi().getSelf().getIncomingOtrClients());
            }
        });

        if (savedInstanceState == null) {
            boolean showSpotifyLogin = getArguments().getBoolean(ZetaPreferencesActivity.SHOW_SPOTIFY_LOGIN, false);
            boolean showOtrDevices = getArguments().getBoolean(ZetaPreferencesActivity.SHOW_OTR_DEVICES, false);
            boolean showAccount = getArguments().getBoolean(ZetaPreferencesActivity.SHOW_ACCOUNT, false);
            boolean showUsernameEdit = getArguments().getBoolean(ZetaPreferencesActivity.SHOW_USERNAME_EDIT);

            final PreferenceManager.OnNavigateToScreenListener navigateToScreenListener = getPreferenceManager().getOnNavigateToScreenListener();
            PreferenceScreen preference = null;
            if (showAccount) {
                preference = (PreferenceScreen) findPreference(getString(R.string.pref_account_screen_key));
            } else if (showSpotifyLogin) {
                preference = (PreferenceScreen) findPreference(getString(R.string.pref_options_screen_key));
                getControllerFactory().getSpotifyController().login(getActivity());
            } else if (showOtrDevices) {
                preference = devicesPreferenceScreenLike.getPreferenceScreen();
            } else if (showUsernameEdit) {
                preference = ((BadgeablePreferenceScreenLike) findPreference(getString(R.string.pref_account_screen_key))).getPreferenceScreen();
            }

            if (preference != null) {
                preference.getExtras().putAll(getArguments());
                navigateToScreenListener.onNavigateToScreen(preference);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getPasswordController().reset();
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        otrClientsObserver.resumeListening();
        otrClientsObserver.forceUpdate();
    }

    @Override
    public void onStop() {
        otrClientsObserver.pauseListening();
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        super.onStop();
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        devicesPreferenceScreenLike.setAccentColor(color);
    }

    public interface Container {
    }
}
