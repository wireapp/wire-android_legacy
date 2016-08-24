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
package com.waz.zclient;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.localytics.android.Localytics;
import com.waz.api.ActiveVoiceChannels;
import com.waz.api.CommonConnections;
import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.MessagesList;
import com.waz.api.NetworkMode;
import com.waz.api.Self;
import com.waz.api.SyncState;
import com.waz.api.User;
import com.waz.api.Verification;
import com.waz.api.VoiceChannel;
import com.waz.model.ConvId;
import com.waz.threading.Threading;
import com.waz.zclient.calling.CallingActivity;
import com.waz.zclient.calling.controllers.CallPermissionsController;
import com.waz.zclient.controllers.accentcolor.AccentColorChangeRequester;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.calling.CallingObserver;
import com.waz.zclient.controllers.navigation.NavigationControllerObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.tracking.events.connect.AcceptedGenericInviteEvent;
import com.waz.zclient.controllers.tracking.events.exception.ExceptionEvent;
import com.waz.zclient.controllers.tracking.events.otr.VerifiedConversationEvent;
import com.waz.zclient.controllers.tracking.events.profile.SignOut;
import com.waz.zclient.controllers.tracking.screens.ApplicationScreen;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.controllers.userpreferences.UserPreferencesController;
import com.waz.zclient.core.api.scala.AppEntryStore;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.core.controllers.tracking.events.Event;
import com.waz.zclient.core.controllers.tracking.events.media.OpenedMediaActionEvent;
import com.waz.zclient.core.controllers.tracking.events.session.LoggedOutEvent;
import com.waz.zclient.core.stores.api.ZMessagingApiStoreObserver;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.core.stores.profile.ProfileStoreObserver;
import com.waz.zclient.pages.main.MainPhoneFragment;
import com.waz.zclient.pages.main.MainTabletFragment;
import com.waz.zclient.pages.main.connectivity.ConnectivityFragment;
import com.waz.zclient.pages.main.grid.GridFragment;
import com.waz.zclient.pages.main.profile.ZetaPreferencesActivity;
import com.waz.zclient.pages.startup.UpdateFragment;
import com.waz.zclient.utils.BuildConfigUtils;
import com.waz.zclient.utils.Emojis;
import com.waz.zclient.utils.HockeyCrashReporting;
import com.waz.zclient.utils.IntentUtils;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.PhoneUtils;
import com.waz.zclient.utils.PhoneUtils.PhoneState;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import net.hockeyapp.android.NativeCrashManager;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;


public class MainActivity extends BaseActivity implements MainPhoneFragment.Container,
                                                          MainTabletFragment.Container,
                                                          GridFragment.Container,
                                                          ConnectivityFragment.Container,
                                                          UpdateFragment.Container,
                                                          ProfileStoreObserver,
                                                          AccentColorObserver,
                                                          ConnectStoreObserver,
                                                          NavigationControllerObserver,
                                                          CallingObserver,
                                                          OtrDeviceLimitFragment.Container,
                                                          ZMessagingApiStoreObserver,
                                                          ConversationStoreObserver {

    // Tags
    public static final String TAG = MainActivity.class.getName();

    public static final int REQUEST_CODE_GOOGLE_PLAY_SERVICES_DIALOG = 56571;
    private static final int LAUNCH_CONVERSATION_CHANGE_DELAY = 123;

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets to RGBA_8888 to ensure fluent gradients.
     */
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Window window = getWindow();
        window.setFormat(PixelFormat.RGBA_8888);
    }

    @Override
    @SuppressWarnings("PMD")
    public void onCreate(final Bundle savedInstanceState) {
        Timber.i("onCreate");
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        super.onCreate(savedInstanceState);

        //Prevent drawing the default background to reduce overdraw
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        setContentView(R.layout.main);

        if (LayoutSpec.isPhone(this)) {
            ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this);
        }

        FragmentManager fragmentManager = getSupportFragmentManager();

        getStoreFactory().getZMessagingApiStore().getApi().getMediaManager();

        initializeControllers();

        if (!getControllerFactory().getUserPreferencesController().showStatusBar()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        if (savedInstanceState == null) {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.add(R.id.fl__offline__container,
                                    ConnectivityFragment.newInstance(),
                                    ConnectivityFragment.TAG);

            if (BuildConfig.SHOW_GRIDOVERLAY) {
                fragmentTransaction.add(R.id.fl_main_grid,
                                        GridFragment.newInstance(),
                                        GridFragment.TAG);
            }
            fragmentTransaction.commit();
        } else {
            getControllerFactory().getNavigationController().onActivityCreated(savedInstanceState);
        }

        if (BuildConfigUtils.isHockeyUpdateEnabled() && !BuildConfigUtils.isLocalBuild(this)) {
            HockeyCrashReporting.checkForUpdates(this);
        }

        getControllerFactory().getTrackingController().appLaunched(getIntent());
        String appCrash = getControllerFactory().getUserPreferencesController().getCrashException();
        String appCrashDetails = getControllerFactory().getUserPreferencesController().getCrashDetails();
        if (appCrash != null) {
            Event exceptionEvent = ExceptionEvent.exception(appCrash, appCrashDetails);
            getControllerFactory().getTrackingController().tagEvent(exceptionEvent);
        }
        getControllerFactory().getLoadTimeLoggerController().appStart();
    }

    @Override
    protected void onResumeFragments() {
        Timber.i("onResumeFragments");
        super.onResumeFragments();
        getStoreFactory().getZMessagingApiStore().addApiObserver(this);
    }

    @Override
    public void onStart() {
        getControllerFactory().getBackgroundController().setSelf(getStoreFactory().getZMessagingApiStore().getApi().getSelf());
        Timber.i("onStart");
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        getStoreFactory().getProfileStore().addProfileStoreObserver(this);
        getStoreFactory().getConnectStore().addConnectRequestObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getNavigationController().addNavigationControllerObserver(this);
        getControllerFactory().getCallingController().addCallingObserver(this);
        getStoreFactory().getConversationStore().addConversationStoreObserver(this);
        handleInvite();
        handleReferral();

        super.onStart();

        //This is needed to drag the user back to the calling activity if they open the app again during a call
        CallingActivity.startIfCallIsActive(this);

        if (!getControllerFactory().getUserPreferencesController().hasCheckedForUnsupportedEmojis(Emojis.VERSION)) {
            Threading.Background().execute(new Runnable() {
                @Override
                public void run() {
                    checkForUnsupportedEmojis();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        Timber.i("onResume");
        super.onResume();
        verifyGooglePlayServicesStatus();
        final boolean trackingEnabled = getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE)
            .getBoolean(getString(R.string.pref_advanced_analytics_enabled_key), true);

        if (trackingEnabled) {
            HockeyCrashReporting.checkForCrashes(getApplicationContext(),
                                                 getControllerFactory().getUserPreferencesController().getDeviceId(),
                                                 getControllerFactory().getTrackingController());
        } else {
            HockeyCrashReporting.deleteCrashReports(getApplicationContext());
            NativeCrashManager.deleteDumpFiles(getApplicationContext());
        }
        getControllerFactory().getTrackingController().appResumed();
        Localytics.setInAppMessageDisplayActivity(this);
        Localytics.handleTestMode(getIntent());
        if (getControllerFactory().getThemeController().isRestartPending()) {
            getControllerFactory().getThemeController().removePendingRestart();
            restartActivity();
        }
    }

    private void restartActivity() {
        finish();
        startActivity(IntentUtils.getAppLaunchIntent(this));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getControllerFactory().getNavigationController().markActivityResumed();
    }

    @Override
    protected void onPause() {
        Timber.i("onPause");
        Localytics.dismissCurrentInAppMessage();
        Localytics.clearInAppMessageDisplayActivity();
        getControllerFactory().getNavigationController().markActivityPaused();
        getControllerFactory().getTrackingController().appPaused();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Timber.i("onSaveInstanceState");
        getControllerFactory().getNavigationController().onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        Timber.i("onStop");
        super.onStop();
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        getControllerFactory().getCallingController().removeCallingObserver(this);
        getStoreFactory().getZMessagingApiStore().removeApiObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getStoreFactory().getConnectStore().removeConnectRequestObserver(this);
        getStoreFactory().getProfileStore().removeProfileStoreObserver(this);
        getControllerFactory().getNavigationController().removeNavigationControllerObserver(this);
        getControllerFactory().getUserPreferencesController().setLastAccentColor(getStoreFactory().getProfileStore().getAccentColor());
        getControllerFactory().getBackgroundController().onStop();
    }

    @Override
    public void onBackPressed() {
        Timber.i("onBackPressed");
        Fragment currentFragment;
        currentFragment = getSupportFragmentManager().findFragmentById(R.id.fl__calling__container);
        if (currentFragment instanceof OnBackPressedListener) {
            boolean consumed = ((OnBackPressedListener) currentFragment).onBackPressed();
            if (consumed) {
                return;
            }
        }
        currentFragment = getSupportFragmentManager().findFragmentById(R.id.fl_main_content);
        if (currentFragment instanceof OnBackPressedListener) {
            boolean consumed = ((OnBackPressedListener) currentFragment).onBackPressed();
            if (consumed) {
                return;
            }
        }
        currentFragment = getSupportFragmentManager().findFragmentById(R.id.fl_main_otr_warning);
        if (currentFragment instanceof OnBackPressedListener) {
            boolean consumed = ((OnBackPressedListener) currentFragment).onBackPressed();
            if (consumed) {
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.i("OnActivity result: %d/%d", requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        getSupportFragmentManager().findFragmentById(R.id.fl_main_content).onActivityResult(requestCode,
                                                                                            resultCode,
                                                                                            data);
        getControllerFactory().getSpotifyController().handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (IntentUtils.isPasswordResetIntent(intent)) {
            onPasswordWasReset();
        }

        getControllerFactory().getTrackingController().appLaunched(intent);
        String appCrash = getControllerFactory().getUserPreferencesController().getCrashException();
        String appCrashDetails = getControllerFactory().getUserPreferencesController().getCrashDetails();
        if (appCrash != null) {
            Event exceptionEvent = ExceptionEvent.exception(appCrash, appCrashDetails);
            getControllerFactory().getTrackingController().tagEvent(exceptionEvent);
        }
        setIntent(intent);

        String page = intent.getStringExtra(LaunchActivity.APP_PAGE);
        if (page == null ||
            TextUtils.isEmpty(page)) {
            return;
        }
        setIntent(IntentUtils.resetAppPage(intent));
        switch (page) {
            case IntentUtils.LOCALYTICS_DEEPLINK_SEARCH:
            case IntentUtils.LOCALYTICS_DEEPLINK_PROFILE:
                restartAppWithPage(page);
                break;
            case IntentUtils.LOCALYTICS_DEEPLINK_SETTINGS:
                startActivity(ZetaPreferencesActivity.getDefaultIntent(this));
                break;
        }
    }

    private void restartAppWithPage(String page) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(LaunchActivity.APP_PAGE, page);
        startActivity(intent);
        finish();
    }

    private void initializeControllers() {
        // Make sure we have a running OrientationController instance
        getControllerFactory().getOrientationController();

        // Here comes code for adding other dependencies to controllers...
        getControllerFactory().getNavigationController().setIsLandscape(ViewUtils.isInLandscape(this));
    }

    private void verifyGooglePlayServicesStatus() {
        int deviceGooglePlayServicesState = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());
        if (deviceGooglePlayServicesState == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED) {
            GooglePlayServicesUtil.getErrorDialog(deviceGooglePlayServicesState,
                                                  this,
                                                  REQUEST_CODE_GOOGLE_PLAY_SERVICES_DIALOG)
                                  .show();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Navigation
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void enterApplication(final Self self) {
        Timber.e("Entering application");

        // step 1 - check if app was started via password reset intent
        if (IntentUtils.isPasswordResetIntent(getIntent())) {
            Timber.e("Password was reset");
            onPasswordWasReset();
            return;
        }

        // step 2 - no one is logged in
        if (!self.isLoggedIn()) {
            // finally - no one is logged in
            Timber.e("No user is logged in");
            openSignUpPage();
            return;
        }

        switch (self.getClientRegistrationState()) {
            case PASSWORD_MISSING:
                if (!TextUtils.isEmpty(self.getEmail())) {
                    startActivity(new Intent(this, OTRSignInActivity.class));
                    finish();
                    return;
                } else {
                    getStoreFactory().getZMessagingApiStore().getApi().logout();
                }
                break;
            case LIMIT_REACHED:
                showUnableToRegisterOtrClientDialog();
                break;
            default:
        }

        onUserLoggedInAndVerified(self);

    }

    private void onPasswordWasReset() {
        getStoreFactory().getZMessagingApiStore().getApi().logout();
        openSignUpPage();
    }

    private void onUserLoggedInAndVerified(Self self) {
        getStoreFactory().getProfileStore().setUser(self);
        getControllerFactory().getAccentColorController().setColor(AccentColorChangeRequester.LOGIN,
                                                                   self.getAccent().getColor());

        final Intent intent = getIntent();
        if (IntentUtils.isLaunchFromNotificationIntent(intent)) {
            final IConversation conversation = getStoreFactory().getConversationStore().getConversation(IntentUtils.getLaunchConversationId(intent));
            final boolean startCallNotificationIntent = IntentUtils.isStartCallNotificationIntent(intent);
            Timber.i("Start from notification with call=%b", startCallNotificationIntent);
            IntentUtils.clearLaunchIntentExtra(intent);
            if (conversation != null) {
                // Only want to swipe over when app has loaded
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getStoreFactory().getConversationStore().setCurrentConversation(conversation, ConversationChangeRequester.NOTIFICATION);
                        if (startCallNotificationIntent) {
                            startCall(false);
                        }
                    }
                }, LAUNCH_CONVERSATION_CHANGE_DELAY);
            }
        } else if (IntentUtils.isLaunchFromSharingIntent(intent)) {
            final IConversation conversation = getStoreFactory().getConversationStore()
                                                                .getConversation(IntentUtils.getLaunchConversationId(intent));
            if (conversation != null) {
                String sharedText = IntentUtils.getLaunchConversationSharedText(intent);
                List<Uri> sharedFileUris = IntentUtils.getLaunchConversationSharedFiles(intent);
                getControllerFactory().getSharingController().setSharedText(sharedText);
                getControllerFactory().getSharingController().setSharedUris(sharedFileUris);
                getControllerFactory().getSharingController().setSharingConversationId(conversation.getId());

                // Only want to swipe over when app has loaded
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getStoreFactory().getConversationStore().setCurrentConversation(conversation, ConversationChangeRequester.SHARING);
                    }
                }, LAUNCH_CONVERSATION_CHANGE_DELAY);
            }
            IntentUtils.clearLaunchIntentExtra(intent);
        }
        setIntent(intent);
        openMainPage();
    }

    /**
     * Depending on the orientation it opens either
     * MainPhoneFragment or MainTabletFragment. At the
     * beginning it checks if it is already setup properly.
     */
    private void openMainPage() {
        if (LayoutSpec.isPhone(this)) {
            if (getSupportFragmentManager().findFragmentByTag(MainPhoneFragment.TAG) == null) {
                replaceMainFragment(new MainPhoneFragment(), MainPhoneFragment.TAG);
            }
            Timber.i("No need to open main fragment");
        } else {
            if (getSupportFragmentManager().findFragmentByTag(MainTabletFragment.TAG) == null) {
                replaceMainFragment(new MainTabletFragment(), MainTabletFragment.TAG);
            }
            Timber.i("No need to open main fragment");
        }
    }

    private void openSignUpPage() {
        Intent intent = new Intent(getApplicationContext(), AppEntryActivity.class);
        startActivity(intent);
        finish();
    }

    private void openForceUpdatePage() {
        Intent intent = new Intent(getApplicationContext(), ForceUpdateActivity.class);
        startActivity(intent);
        finish();
    }

    private void replaceMainFragment(Fragment fragment, String TAG) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fl_main_content, fragment, TAG).commit();
    }

    private void handleInvite() {
        String token = getControllerFactory().getUserPreferencesController().getGenericInvitationToken();
        getControllerFactory().getUserPreferencesController().setGenericInvitationToken(null);
        if (TextUtils.isEmpty(token) ||
            TextUtils.equals(token, AppEntryStore.GENERAL_GENERIC_INVITE_TOKEN)) {
            return;
        }
        getStoreFactory().getConnectStore().requestConnection(token);
        getControllerFactory().getTrackingController().tagEvent(new AcceptedGenericInviteEvent());
    }

    private void handleReferral() {
        String referralToken = getControllerFactory().getUserPreferencesController().getReferralToken();
        getControllerFactory().getUserPreferencesController().
            setReferralToken(null);
        if (TextUtils.isEmpty(referralToken) ||
            TextUtils.equals(referralToken, AppEntryStore.GENERAL_GENERIC_INVITE_TOKEN)) {
            return;
        }
        getStoreFactory().getConnectStore().requestConnection(referralToken);
        getControllerFactory().getTrackingController().tagEvent(new AcceptedGenericInviteEvent());
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onLogout() {
        Timber.i("onLogout");
        getStoreFactory().reset();
        getControllerFactory().getPickUserController().hideUserProfile();
        getControllerFactory().getUserPreferencesController().reset();
        getControllerFactory().getSpotifyController().logout();
        getStoreFactory().getConversationStore().onLogout();
        getControllerFactory().getNavigationController().resetPagerPositionToDefault();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        finish();
        startActivity(intent);
    }

    @Override
    public void onForceClientUpdate() {
        openForceUpdatePage();
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        getControllerFactory().getUserPreferencesController().setLastAccentColor(color);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  GroupCallingStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

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

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  NavigationControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPageVisible(Page page) {
        getControllerFactory().getGlobalLayoutController().setSoftInputModeForPage(page);
        getControllerFactory().getNavigationController().setPagerSettingForPage(page);

        switch (page) {
            case CONVERSATION_LIST:
                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.CONVERSATION_LIST);
                break;
            case MESSAGE_STREAM:
                IConversation currentConversation =  getStoreFactory().getConversationStore().getCurrentConversation();
                if (currentConversation == null) {
                    break;
                }
                String conversationName = currentConversation.getName();
                if (currentConversation.getType() == IConversation.Type.ONE_TO_ONE &&
                    ((conversationName.toLowerCase(Locale.getDefault()).contains("otto") &&
                      conversationName.toLowerCase(Locale.getDefault()).contains("bot")) ||
                     TextUtils.equals(currentConversation.getOtherParticipant().getEmail(), "ottobot@wire.com"))) {
                    getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.CONVERSATION__BOT);
                } else {
                    getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.CONVERSATION);
                }
                break;
            case CAMERA:
                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.CAMERA);
                break;
            case DRAWING:
                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.DRAW_SKETCH);
                break;
            case CONNECT_REQUEST_INBOX:
                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.CONVERSATION__INBOX);
                break;
            case PENDING_CONNECT_REQUEST_AS_CONVERSATION:
                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.CONVERSATION__PENDING);
                break;
            case PARTICIPANT:
                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.CONVERSATION__PARTICIPANTS);
                break;
            case PICK_USER_ADD_TO_CONVERSATION:
                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.START_UI__ADD_TO_CONVERSATION);
                break;
            case PICK_USER:
                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.START_UI);
                break;
        }
    }

    @Override
    public void onPageStateHasChanged(Page page) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConnectStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnectUserUpdated(User user, IConnectStore.UserRequester usertype) {}

    @Override
    public void onCommonConnectionsUpdated(CommonConnections commonConnections) {}

    @Override
    public void onInviteRequestSent(IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.INVITE);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Browser
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onOpenUrl(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browserIntent);
        } catch (Exception e) {
            Timber.e("Failed to open URL: %s", url);
        }
    }

    private void showUnableToRegisterOtrClientDialog() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fl_main_otr_warning);
        if (fragment != null) {
            return;
        }
        getSupportFragmentManager().beginTransaction()
                                   .replace(R.id.fl_main_otr_warning,
                                        OtrDeviceLimitFragment.newInstance(),
                                        OtrDeviceLimitFragment.TAG)
                                   .addToBackStack(OtrDeviceLimitFragment.TAG)
                                   .commitAllowingStateLoss();
    }

    @Override
    public void logout() {
        getSupportFragmentManager().popBackStackImmediate();
        // TODO: Remove old SignOut event AN-4232
        getControllerFactory().getTrackingController().tagEvent(new SignOut());
        getControllerFactory().getTrackingController().tagEvent(new LoggedOutEvent());
        getStoreFactory().getZMessagingApiStore().logout();
    }

    @Override
    public void manageDevices() {
        getSupportFragmentManager().popBackStackImmediate();
        startActivity(ZetaPreferencesActivity.getOtrDevicesPreferencesIntent(this));
    }

    @Override
    public void dismissOtrDeviceLimitFragment() {
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Override
    public void onInitialized(Self self) {
        enterApplication(self);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Cursor callback
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStartCall(boolean withVideo) {
        handleOnStartCall(withVideo);
        boolean isGroupConversation = getStoreFactory().getConversationStore().getCurrentConversation().getType() == IConversation.Type.GROUP;
        if (withVideo) {
            getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.videocall(isGroupConversation));
        } else {
            getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.audiocall(isGroupConversation));
        }
    }

    private void handleOnStartCall(final boolean withVideo) {
        if (PhoneUtils.getPhoneState(this) == PhoneState.IDLE && getActiveVoiceChannels().hasOngoingCall()) {
            cannotStartAlreadyHaveVoiceActive(withVideo);
        } else if (PhoneUtils.getPhoneState(this) != PhoneState.IDLE) {
            cannotStartGSM();
        } else {
            startCallIfInternet(withVideo);
        }
    }

    private ActiveVoiceChannels getActiveVoiceChannels() {
        return getStoreFactory().getZMessagingApiStore().getApi().getActiveVoiceChannels();
    }

    private void startCall(boolean withVideo) {
        final IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();
        if (currentConversation == null) {
            return;
        }
        final VoiceChannel voiceChannel = currentConversation.getVoiceChannel();
        if (currentConversation.hasUnjoinedCall() && voiceChannel.isVideoCall() != withVideo) {
            ViewUtils.showAlertDialog(this,
                                      getString(R.string.calling__cannot_start__ongoing_different_kind__title, currentConversation.getName()),
                                      getString(R.string.calling__cannot_start__ongoing_different_kind__message),
                                      getString(R.string.calling__cannot_start__button),
                                      null,
                                      true);
            return;
        }
        injectJava(CallPermissionsController.class).startCall(new ConvId(voiceChannel.getConversation().getId()), withVideo);
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  VoiceChannel.JoinCallback
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private void cannotStartAlreadyHaveVoiceActive(final boolean withVideo) {
        ViewUtils.showAlertDialog(this,
                                  R.string.calling__cannot_start__ongoing_voice__title,
                                  R.string.calling__cannot_start__ongoing_voice__message,
                                  R.string.calling__cannot_start__ongoing_voice__button_positive,
                                  R.string.calling__cannot_start__ongoing_voice__button_negative,
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          if (getStoreFactory() == null ||
                                              getStoreFactory().isTornDown()) {
                                              return;
                                          }
                                          final ActiveVoiceChannels activeVoiceChannels = getActiveVoiceChannels();
                                          if (activeVoiceChannels.getOngoingCall() == null) {
                                              return;
                                          }
                                          activeVoiceChannels.getOngoingCall().leave();
                                          new Handler().postDelayed(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            if (getStoreFactory() == null ||
                                                                                getStoreFactory().isTornDown()) {
                                                                                return;
                                                                            }
                                                                            startCall(withVideo);
                                                                            getControllerFactory().getTrackingController()
                                                                                                  .updateSessionAggregates(RangedAttribute.VOICE_CALLS_INITIATED);
                                                                        }
                                                                    },
                                                                    getResources().getInteger(R.integer.calling__new_outgoing_call__delay_after_hangup));
                                      }
                                  },
                                  null);
    }

    private void cannotStartGSM() {
        ViewUtils.showAlertDialog(this,
                                  R.string.calling__cannot_start__title,
                                  R.string.calling__cannot_start__message,
                                  R.string.calling__cannot_start__button,
                                  null,
                                  true);
    }

    private void startCallIfInternet(final boolean withVideo) {
        getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
            @Override
            public void execute(NetworkMode networkMode) {
                switch (networkMode) {
                    case _2G:
                        ViewUtils.showAlertDialog(MainActivity.this,
                                                  R.string.calling__slow_connection__title,
                                                  R.string.calling__slow_connection__message,
                                                  R.string.calling__slow_connection__button,
                                                  null,
                                                  true);
                        break;
                    case EDGE:
                        if (withVideo) {
                            ViewUtils.showAlertDialog(MainActivity.this,
                                                      R.string.calling__slow_connection__title,
                                                      R.string.calling__video_call__slow_connection__message,
                                                      R.string.calling__slow_connection__button,
                                                      new DialogInterface.OnClickListener() {
                                                          @Override
                                                          public void onClick(DialogInterface dialogInterface, int i) {
                                                              startCall(true);
                                                          }
                                                      },
                                                      true);
                            break;
                        }
                    case _3G:
                    case _4G:
                    case WIFI:
                        startCall(withVideo);
                        break;
                }
            }

            @Override
            public void onNoNetwork() {
                ViewUtils.showAlertDialog(MainActivity.this,
                                          R.string.alert_dialog__no_network__header,
                                          R.string.calling__call_drop__message,
                                          R.string.alert_dialog__confirmation,
                                          new DialogInterface.OnClickListener() {
                                              @Override
                                              public void onClick(DialogInterface dialog, int which) {
                                              }
                                          }, false);
            }
        });
    }

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
    public void onVerificationStateChanged(String conversationId,
                                           Verification previousVerification,
                                           Verification currentVerification) {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return;
        }
        if (previousVerification != Verification.VERIFIED && currentVerification == Verification.VERIFIED) {
            getControllerFactory().getTrackingController().tagEvent(new VerifiedConversationEvent());
        }
    }

    private void checkForUnsupportedEmojis() {
        if (getControllerFactory() == null ||
            getControllerFactory().isTornDown()) {
            return;
        }
        IUserPreferencesController userPreferencesController = getControllerFactory().getUserPreferencesController();
        Collection<String> unsupported = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Paint paint = new Paint();
            for (String[] array : Emojis.getAllEmojisSortedByCategory()) {
                for (String emoji : array) {
                    if (!paint.hasGlyph(emoji)) {
                        unsupported.add(emoji);
                    }
                }
            }
        } else {
            for (String[] array : Emojis.getAllEmojisSortedByCategory()) {
                unsupported.addAll(Arrays.asList(array));
            }
            unsupported = StringUtils.getMissingInFont(unsupported);
        }
        if (!unsupported.isEmpty()) {
            userPreferencesController.setUnsupportedEmoji(unsupported, Emojis.VERSION);
        }
    }
}
