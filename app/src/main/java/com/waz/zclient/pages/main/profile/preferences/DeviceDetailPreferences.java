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

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.widget.Toast;
import com.waz.api.Fingerprint;
import com.waz.api.NetworkMode;
import com.waz.api.OtrClient;
import com.waz.api.Subscriber;
import com.waz.api.Subscription;
import com.waz.api.UiSignal;
import com.waz.api.UpdateListener;
import com.waz.api.Verification;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.tracking.events.otr.RemovedOwnOtrClientEvent;
import com.waz.zclient.controllers.tracking.events.otr.UnverifiedOwnOtrClientEvent;
import com.waz.zclient.controllers.tracking.events.otr.VerifiedOwnOtrClientEvent;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.pages.BasePreferenceFragment;
import com.waz.zclient.pages.main.profile.preferences.dialogs.RemoveDevicePreferenceDialogFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.ViewUtils;
import net.xpece.android.support.preference.PreferenceCategory;
import net.xpece.android.support.preference.SwitchPreference;
import timber.log.Timber;

public class DeviceDetailPreferences extends BasePreferenceFragment<DeviceDetailPreferences.Container>
    implements RemoveDevicePreferenceDialogFragment.Container {

    public static final String TAG = DeviceDetailPreferences.class.getName();
    public static final String PREFS_OTR_CLIENT = "PREFS_OTR_CLIENT";
    public static final String PREFS_CURRENT_DEVICE = "PREFS_CURRENT_DEVICE";

    private OtrClient otrClient;
    private final UpdateListener otrClientUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            updateOtrClientDetails();
        }
    };
    private Subscription fingerprintSubscription;
    private UiSignal<Fingerprint> fingerprint;
    private boolean currentDevice;

    public static DeviceDetailPreferences newInstance(String rootKey, Bundle extras) {
        DeviceDetailPreferences f = new DeviceDetailPreferences();
        Bundle args = new Bundle(extras);
        args.putString(ARG_PREFERENCE_ROOT, rootKey);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreatePreferences2(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences2(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.preferences_device_details);
        currentDevice = getArguments().getBoolean(PREFS_CURRENT_DEVICE, false);
        otrClient = getArguments().getParcelable(PREFS_OTR_CLIENT);
        otrClient.addUpdateListener(otrClientUpdateListener);
        updateOtrClientDetails();
    }

    @Override
    public void onStop() {
        if (fingerprintSubscription != null) {
            fingerprintSubscription.cancel();
            fingerprintSubscription = null;
        }
        fingerprint = null;
        if (otrClient != null) {
            otrClient.removeUpdateListener(otrClientUpdateListener);
            otrClient = null;
        }
        super.onStop();
    }

    private void updateOtrClientDetails() {
        // Name
        Preference preference = findPreference(getString(R.string.pref_device_details_device_key));
        preference.setTitle(DevicesPreferencesUtil.getTitle(getActivity(), otrClient));
        preference.setSummary(DevicesPreferencesUtil.getSummary(getActivity(), otrClient, true));

        // Fingerprint
        final FingerprintPreference fingerPrintPreference = (FingerprintPreference) findPreference(getString(R.string.pref_device_details_fingerprint_key));
        if (fingerprint == null) {
            fingerprint = otrClient.getFingerprint();
            fingerprintSubscription = fingerprint.subscribe(new Subscriber<Fingerprint>() {
                @Override
                public void next(Fingerprint fingerprint) {
                    fingerPrintPreference.setFingerprint(fingerprint);
                }
            });
        }

        //Trust
        final SwitchPreference verifySwitchPreference = (SwitchPreference) findPreference(getString(R.string.pref_device_details_trust_key));
        if (currentDevice) {
            PreferenceCategory group = (PreferenceCategory) findPreference(getString(R.string.pref_device_details_fingerprint_category_key));
            group.removePreference(verifySwitchPreference);
        } else {
            verifySwitchPreference.setChecked(otrClient.getVerified() == Verification.VERIFIED);
            // Note: Using OnPreferenceClickListener as it was some issues with getting
            //       OnPreferenceChangeListener to work.
            verifySwitchPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    boolean newVerifiredValue = otrClient.getVerified() != Verification.VERIFIED;
                    otrClient.setVerified(newVerifiredValue);
                    verifySwitchPreference.setChecked(newVerifiredValue);
                    trackVerified(newVerifiredValue);
                    return true;
                }
            });
        }

        // Remove
        if (currentDevice) {
            Preference actionsPreference = findPreference(getString(R.string.pref_device_details_actions_category_key));
            PreferenceScreen screen = (PreferenceScreen) findPreference(getString(R.string.pref_device_details_screen_key));
            screen.removePreference(actionsPreference);
        } else {
            Preference resetSessionPreference = findPreference(getString(R.string.pref_device_details_reset_session_key));
            resetSessionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    resetSession();
                    return true;
                }
            });
            preference = findPreference(getString(R.string.pref_device_details_remove_key));
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
                        @Override
                        public void execute(NetworkMode networkMode) {
                            if (getControllerFactory().getPasswordController().hasPassword()) {
                                deleteCurrentDevice();
                            } else {
                                showRemoveDeviceDialog();
                            }
                        }

                        @Override
                        public void onNoNetwork() {
                            ViewUtils.showAlertDialog(DeviceDetailPreferences.this.getActivity(),
                                                      R.string.otr__remove_device__no_internet__title,
                                                      R.string.otr__remove_device__no_internet__message,
                                                      R.string.otr__remove_device__no_internet__button,
                                                      null,
                                                      true);
                        }
                    });
                    return true;
                }
            });
        }
    }

    private void deleteCurrentDevice() {
        otrClient.delete(getControllerFactory().getPasswordController().getPassword(),
                         new OtrClient.DeleteCallback() {
                             @Override
                             public void onClientDeleted(OtrClient otrClient) {
                                 if (getActivity() == null ||
                                     getControllerFactory() == null ||
                                     getControllerFactory().isTornDown()) {
                                     return;
                                 }
                                 ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new RemovedOwnOtrClientEvent());
                                 onCurrentDeviceDeleted();
                             }

                             @Override
                             public void onDeleteFailed(String error) {
                                 Timber.e("Remove client failed: %s", error);
                                 if (getActivity() == null ||
                                     getControllerFactory() == null ||
                                     getControllerFactory().isTornDown()) {
                                     return;
                                 }
                                 showRemoveDeviceDialog();
                             }
                         });
    }

    private void showRemoveDeviceDialog() {
        getChildFragmentManager().beginTransaction()
                                 .add(RemoveDevicePreferenceDialogFragment.newInstance(otrClient.getModel()),
                                      RemoveDevicePreferenceDialogFragment.TAG)
                                 .addToBackStack(RemoveDevicePreferenceDialogFragment.TAG)
                                 .commit();
    }

    private void trackVerified(boolean verified) {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return;
        }
        if (verified) {
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new VerifiedOwnOtrClientEvent());
        } else {
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new UnverifiedOwnOtrClientEvent());
        }
    }

    private void resetSession() {
        otrClient.resetSession(new OtrClient.ResetCallback() {
            @Override
            public void onSessionReset(OtrClient otrClient) {
                Toast.makeText(getActivity(), R.string.otr__reset_session__message_ok, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSessionResetFailed(int i, String s, String s1) {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.empty_string,
                                          R.string.otr__reset_session__message_fail,
                                          R.string.otr__reset_session__button_ok,
                                          R.string.otr__reset_session__button_fail, null, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            resetSession();
                        }
                    });
            }
        });
    }

    @Override
    public OtrClient getOtrClient() {
        return otrClient;
    }

    @Override
    public void onCurrentDeviceDeleted() {
        getActivity().onBackPressed();
    }

    public interface Container {
    }
}
