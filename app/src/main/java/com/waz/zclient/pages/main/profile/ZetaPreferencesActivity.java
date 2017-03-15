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
package com.waz.zclient.pages.main.profile;

import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.view.MenuItem;
import android.widget.Toast;
import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.NetworkMode;
import com.waz.api.Self;
import com.waz.api.SyncState;
import com.waz.zclient.BasePreferenceActivity;
import com.waz.zclient.MainActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorChangeRequester;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedProfilePictureEvent;
import com.waz.zclient.core.stores.api.ZMessagingApiStoreObserver;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.core.stores.profile.ProfileStoreObserver;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.pages.main.profile.camera.CameraFragment;
import com.waz.zclient.pages.main.profile.preferences.AboutPreferences;
import com.waz.zclient.pages.main.profile.preferences.AccountPreferences;
import com.waz.zclient.pages.main.profile.preferences.AdvancedPreferences;
import com.waz.zclient.pages.main.profile.preferences.DeveloperPreferences;
import com.waz.zclient.pages.main.profile.preferences.DeviceDetailPreferences;
import com.waz.zclient.pages.main.profile.preferences.DevicesPreferences;
import com.waz.zclient.pages.main.profile.preferences.OptionsPreferences;
import com.waz.zclient.pages.main.profile.preferences.RootPreferences;
import com.waz.zclient.pages.main.profile.preferences.SupportPreferences;
import com.waz.zclient.pages.main.profile.preferences.dialogs.WireRingtonePreferenceDialogFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;

import timber.log.Timber;

public class ZetaPreferencesActivity extends BasePreferenceActivity implements AccountPreferences.Container,
                                                                               AdvancedPreferences.Container,
                                                                               OptionsPreferences.Container,
                                                                               SupportPreferences.Container,
                                                                               AboutPreferences.Container,
                                                                               DeveloperPreferences.Container,
                                                                               DevicesPreferences.Container,
                                                                               DeviceDetailPreferences.Container,
                                                                               ConversationStoreObserver,
                                                                               ZMessagingApiStoreObserver,
                                                                               RootPreferences.Container,
                                                                               ProfileStoreObserver,
                                                                               AccentColorObserver,
                                                                               CameraFragment.Container {
    public static final String SHOW_SPOTIFY_LOGIN = "SHOW_SPOTIFY_LOGIN";
    public static final String SHOW_OTR_DEVICES = "SHOW_OTR_DEVICES";
    public static final String SHOW_ACCOUNT = "SHOW_ACCOUNT";
    public static final String SHOW_USERNAME_EDIT = "SHOW_USERNAME_EDIT";

    public static Intent getDefaultIntent(Context context) {
        return new Intent(context, ZetaPreferencesActivity.class);
    }

    public static Intent getSpotifyLoginIntent(Context context) {
        Intent intent = getDefaultIntent(context);
        intent.putExtra(SHOW_SPOTIFY_LOGIN, true);
        return intent;
    }

    public static Intent getOtrDevicesPreferencesIntent(Context context) {
        Intent intent = getDefaultIntent(context);
        intent.putExtra(SHOW_OTR_DEVICES, true);
        return intent;
    }

    public static Intent getUsernameEditPreferencesIntent(Context context) {
        Intent intent = getDefaultIntent(context);
        intent.putExtra(SHOW_USERNAME_EDIT, true);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (LayoutSpec.isPhone(this)) {
            ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                                       .add(R.id.content, RootPreferences.newInstance(null, getIntent().getExtras()), RootPreferences.TAG)
                                       .commit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        getControllerFactory().getSpotifyController().handleActivityResult(requestCode, resultCode, data);
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fl__root__camera);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().getZMessagingApiStore().addApiObserver(this);
        getStoreFactory().getConversationStore().addConversationStoreObserver(this);
        getStoreFactory().getProfileStore().addProfileStoreObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getCameraController().addCameraActionObserver(this);
    }

    @Override
    public void onStop() {
        getControllerFactory().getCameraController().removeCameraActionObserver(this);
        getStoreFactory().getProfileStore().removeProfileStoreObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        getStoreFactory().getZMessagingApiStore().removeApiObserver(this);
        super.onStop();
    }

    @Override
    public void onInitialized(Self self) { }

    @Override
    public void onLogout() {
        Timber.i("onLogout");
        getStoreFactory().reset();
        getControllerFactory().getPickUserController().hideUserProfile();
        getControllerFactory().getUserPreferencesController().reset();
        getStoreFactory().getConversationStore().onLogout();
        getControllerFactory().getNavigationController().resetPagerPositionToDefault();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onForceClientUpdate() { }

    @Override
    public void onConversationListUpdated(@NonNull ConversationsList conversationsList) {

    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    @Override
    public void onCurrentConversationHasChanged(IConversation fromConversation,
                                                IConversation toConversation,
                                                ConversationChangeRequester conversationChangerSender) {

    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    @Override
    public boolean onPreferenceDisplayDialog(PreferenceFragmentCompat preferenceFragmentCompat, Preference preference) {
        final String key = preference.getKey();
        final DialogFragment f;
        if (preference.getKey().equals(getString(R.string.pref_options_ringtones_ping_key)) ||
            preference.getKey().equals(getString(R.string.pref_options_ringtones_text_key)) ||
            preference.getKey().equals(getString(R.string.pref_options_ringtones_ringtone_key))) {
            final int defaultId = preference.getExtras().getInt(WireRingtonePreferenceDialogFragment.EXTRA_DEFAULT);
            f = WireRingtonePreferenceDialogFragment.newInstance(key, defaultId);
        } else {
            return false;
        }
        f.setTargetFragment(preferenceFragmentCompat, 0);
        f.show(getSupportFragmentManager(), key);
        return true;
    }

    @Override
    public PreferenceFragmentCompat onBuildPreferenceFragment(PreferenceScreen preferenceScreen) {
        final String rootKey = preferenceScreen.getKey();
        final Bundle extras = preferenceScreen.getExtras();
        final PreferenceFragmentCompat instance;
        if (rootKey.equals(getString(R.string.pref_account_screen_key))) {
            instance = AccountPreferences.newInstance(rootKey, extras);
        } else if (rootKey.equals(getString(R.string.pref_about_screen_key))) {
            instance = AboutPreferences.newInstance(rootKey, extras);
        } else if (rootKey.equals(getString(R.string.pref_options_screen_key))) {
            instance = OptionsPreferences.newInstance(rootKey, extras);
        } else if (rootKey.equals(getString(R.string.pref_support_screen_key))) {
            instance = SupportPreferences.newInstance(rootKey, extras);
        } else if (rootKey.equals(getString(R.string.pref_advanced_screen_key))) {
            instance = AdvancedPreferences.newInstance(rootKey, extras);
        } else if (rootKey.equals(getString(R.string.pref_developer_screen_key))) {
            instance = DeveloperPreferences.newInstance(rootKey, extras);
        } else if (rootKey.equals(getString(R.string.pref_devices_screen_key))) {
            instance = DevicesPreferences.newInstance(rootKey, extras);
        } else if (rootKey.equals(getString(R.string.pref_device_details_screen_key))) {
            instance = DeviceDetailPreferences.newInstance(rootKey, extras);
        } else {
            instance = RootPreferences.newInstance(rootKey, extras);
        }
        resetIntentExtras(preferenceScreen);
        return instance;
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        getControllerFactory().getUserPreferencesController().setLastAccentColor(color);
    }

    @Override
    public void onAccentColorChangedRemotely(Object sender, int color) {
        getControllerFactory().getAccentColorController().setColor(AccentColorChangeRequester.REMOTE, color);
    }

    @Override
    public void onMyNameHasChanged(Object sender, String myName) {
    }

    @Override
    public void onMyEmailHasChanged(String myEmail, boolean isVerified) {
    }

    @Override
    public void onMyPhoneHasChanged(String myPhone, boolean isVerified) {

    }

    @Override
    public void onPhoneUpdateFailed(String myPhone, int errorCode, String message, String label) {

    }

    @Override
    public void onMyEmailAndPasswordHasChanged(String myEmail) {

    }

    private void resetIntentExtras(PreferenceScreen preferenceScreen) {
        preferenceScreen.getExtras().remove(ZetaPreferencesActivity.SHOW_SPOTIFY_LOGIN);
        preferenceScreen.getExtras().remove(ZetaPreferencesActivity.SHOW_OTR_DEVICES);
        preferenceScreen.getExtras().remove(ZetaPreferencesActivity.SHOW_ACCOUNT);
        preferenceScreen.getExtras().remove(ZetaPreferencesActivity.SHOW_USERNAME_EDIT);
    }

    @Override
    public void onBitmapSelected(final ImageAsset imageAsset, boolean imageFromCamera, CameraContext cameraContext) {
        if (cameraContext != CameraContext.SETTINGS) {
            return;
        }
        getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
            @Override
            public void execute(NetworkMode networkMode) {
                getStoreFactory().getProfileStore().setUserPicture(imageAsset);
                getControllerFactory().getBackgroundController().setImageAsset(imageAsset);
                injectJava(GlobalTrackingController.class).tagEvent(new ChangedProfilePictureEvent());
            }

            @Override
            public void onNoNetwork() {
                ViewUtils.showAlertDialog(ZetaPreferencesActivity.this,
                                          R.string.alert_dialog__no_network__header,
                                          R.string.profile_pic__no_network__message,
                                          R.string.alert_dialog__confirmation,
                                          null, true);
            }
        });

        getSupportFragmentManager().popBackStack(CameraFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onCameraNotAvailable() {
        Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOpenCamera(CameraContext cameraContext) {
        if (getSupportFragmentManager().findFragmentByTag(CameraFragment.TAG) != null) {
            return;
        }
        getSupportFragmentManager().beginTransaction()
                                   .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                   .add(R.id.fl__root__camera,
                                            CameraFragment.newInstance(cameraContext),
                                            CameraFragment.TAG)
                                   .addToBackStack(CameraFragment.TAG)
                                   .commit();
    }

    @Override
    public void onCloseCamera(CameraContext cameraContext) {
        getSupportFragmentManager().popBackStack(CameraFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onMyUsernameHasChanged(String myUsername) {
    }
}
