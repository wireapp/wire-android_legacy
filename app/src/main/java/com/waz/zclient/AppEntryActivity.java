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

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.widget.Toast;
import com.localytics.android.Localytics;
import com.waz.api.BitmapCallback;
import com.waz.api.ImageAsset;
import com.waz.api.ImageAssetFactory;
import com.waz.api.LoadHandle;
import com.waz.api.Self;
import com.waz.service.AccountsService;
import com.waz.service.ZMessaging;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.zclient.controllers.navigation.NavigationControllerObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.tracking.screens.ApplicationScreen;
import com.waz.zclient.core.api.scala.AppEntryStore;
import com.waz.zclient.core.controllers.tracking.attributes.Attribute;
import com.waz.zclient.core.controllers.tracking.attributes.RegistrationEventContext;
import com.waz.zclient.core.controllers.tracking.events.Event;
import com.waz.zclient.core.controllers.tracking.events.registration.OpenedPhoneRegistrationFromInviteEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.SucceededWithRegistrationEvent;
import com.waz.zclient.core.stores.api.ZMessagingApiStoreObserver;
import com.waz.zclient.core.stores.appentry.AppEntryState;
import com.waz.zclient.core.stores.appentry.AppEntryStateCallback;
import com.waz.zclient.newreg.fragments.EmailInvitationFragment;
import com.waz.zclient.newreg.fragments.EmailRegisterFragment;
import com.waz.zclient.newreg.fragments.EmailSignInFragment;
import com.waz.zclient.newreg.fragments.EmailVerifyEmailFragment;
import com.waz.zclient.newreg.fragments.FirstLaunchAfterLoginFragment;
import com.waz.zclient.newreg.fragments.OTRPhoneAddEmailFragment;
import com.waz.zclient.newreg.fragments.PhoneAddEmailFragment;
import com.waz.zclient.newreg.fragments.PhoneInvitationFragment;
import com.waz.zclient.newreg.fragments.PhoneRegisterFragment;
import com.waz.zclient.newreg.fragments.PhoneSetNameFragment;
import com.waz.zclient.newreg.fragments.PhoneSignInFragment;
import com.waz.zclient.newreg.fragments.PhoneVerifyEmailFragment;
import com.waz.zclient.newreg.fragments.SignUpPhotoFragment;
import com.waz.zclient.newreg.fragments.VerifyPhoneFragment;
import com.waz.zclient.newreg.fragments.WelcomeEmailFragment;
import com.waz.zclient.newreg.fragments.country.CountryController;
import com.waz.zclient.newreg.fragments.country.CountryDialogFragment;
import com.waz.zclient.preferences.PreferencesController;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.HockeyCrashReporting;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.ZTimeFormatter;
import com.waz.zclient.views.LoadingIndicatorView;
import net.hockeyapp.android.NativeCrashManager;
import timber.log.Timber;

import static com.waz.zclient.newreg.fragments.SignUpPhotoFragment.UNSPLASH_API_URL;

public class AppEntryActivity extends BaseActivity implements VerifyPhoneFragment.Container,
                                                              PhoneRegisterFragment.Container,
                                                              PhoneSignInFragment.Container,
                                                              PhoneSetNameFragment.Container,
                                                              PhoneAddEmailFragment.Container,
                                                              OTRPhoneAddEmailFragment.Container,
                                                              PhoneVerifyEmailFragment.Container,
                                                              SignUpPhotoFragment.Container,
                                                              EmailRegisterFragment.Container,
                                                              EmailSignInFragment.Container,
                                                              EmailVerifyEmailFragment.Container,
                                                              WelcomeEmailFragment.Container,
                                                              EmailInvitationFragment.Container,
                                                              PhoneInvitationFragment.Container,
                                                              InAppWebViewFragment.Container,
                                                              CountryDialogFragment.Container,
                                                              FirstLaunchAfterLoginFragment.Container,
                                                              NavigationControllerObserver,
                                                              AppEntryStateCallback,
                                                              ZMessagingApiStoreObserver {

    public static final String TAG = AppEntryActivity.class.getName();
    private static final String HTTPS_PREFIX = "https://";
    private static final String HTTP_PREFIX = "http://";
    public static final int PREFETCH_IMAGE_WIDTH = 4;

    private ImageAsset unsplashInitImageAsset;
    private LoadHandle unsplashInitLoadHandle = null;
    private LoadingIndicatorView progressView;
    private CountryController countryController;
    private int accentColor;
    private boolean createdFromSavedInstance;
    private boolean isPaused;

    @Override
    public void onBackPressed() {
        SignUpPhotoFragment fragment = (SignUpPhotoFragment) getSupportFragmentManager().findFragmentByTag(
            SignUpPhotoFragment.TAG);
        if (fragment != null && fragment.onBackPressed()) {
            return;
        }
        OTRPhoneAddEmailFragment otrPhoneAddEmailFragment = (OTRPhoneAddEmailFragment) getSupportFragmentManager().findFragmentByTag(
            OTRPhoneAddEmailFragment.TAG);
        if (otrPhoneAddEmailFragment != null) {
            getSupportFragmentManager().popBackStackImmediate(R.id.fl_main_content,
                                                              FragmentManager.POP_BACK_STACK_INCLUSIVE);
            getStoreFactory().zMessagingApiStore().logout();
            getStoreFactory().appEntryStore().setState(AppEntryState.PHONE_SIGN_IN);
            return;
        }
        if (!getStoreFactory().appEntryStore().onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_Dark);

        ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this);

        setContentView(R.layout.activity_signup);

        countryController = new CountryController(this);

        progressView = ViewUtils.getView(this, R.id.liv__progress);

        // always disable progress bar at the beginning
        enableProgress(false);

        createdFromSavedInstance = savedInstanceState != null;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            accentColor = getResources().getColor(R.color.text__primary_dark);
        } else {
            accentColor = getResources().getColor(R.color.text__primary_dark, getTheme());
        }

        if (unsplashInitLoadHandle == null && unsplashInitImageAsset == null) {
            unsplashInitImageAsset = ImageAssetFactory.getImageAsset(AndroidURIUtil.parse(UNSPLASH_API_URL));

            // This is just to force that SE will download the image so that it is probably ready when we are at the
            // set picture screen
            unsplashInitLoadHandle = unsplashInitImageAsset.getSingleBitmap(PREFETCH_IMAGE_WIDTH,
                                                                            new BitmapCallback() {
                                                                                @Override
                                                                                public void onBitmapLoaded(Bitmap b) {}
                                                                            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getNavigationController().addNavigationControllerObserver(this);
        getStoreFactory().zMessagingApiStore().addApiObserver(this);
        getStoreFactory().appEntryStore().setCallback(this);

        if (!createdFromSavedInstance) {
            getStoreFactory().appEntryStore().resumeAppEntry(getStoreFactory().zMessagingApiStore().getApi().getSelf(),
                                                                getControllerFactory().getUserPreferencesController().getPersonalInvitationToken());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        final boolean trackingEnabled = injectJava(PreferencesController.class).isAnalyticsEnabled();

        if (trackingEnabled) {
            HockeyCrashReporting.checkForCrashes(getApplicationContext(),
                                                 getControllerFactory().getUserPreferencesController().getDeviceId(),
                                                 injectJava(GlobalTrackingController.class));
        } else {
            HockeyCrashReporting.deleteCrashReports(getApplicationContext());
            NativeCrashManager.deleteDumpFiles(getApplicationContext());
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (isPaused) {
            isPaused = false;
            getStoreFactory().appEntryStore().triggerStateUpdate();
        }
    }

    @Override
    protected void onPause() {
        isPaused = true;
        super.onPause();
    }

    @Override
    public void onStop() {
        getControllerFactory().getNavigationController().removeNavigationControllerObserver(this);
        getStoreFactory().appEntryStore().setCallback(null);
        getStoreFactory().zMessagingApiStore().removeApiObserver(this);
        if (unsplashInitLoadHandle != null) {
            unsplashInitLoadHandle.cancel();
            unsplashInitLoadHandle = null;
        }
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.i("OnActivity result: %d/%d", requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        getSupportFragmentManager().findFragmentById(R.id.fl_main_content).onActivityResult(requestCode,
                                                                                            resultCode,
                                                                                            data);
    }

    @Override
    public void onDestroy() {
        getStoreFactory().appEntryStore().clearCurrentState();
        super.onDestroy();
    }

    @Override
    public void enableProgress(boolean enabled) {
        if (enabled) {
            progressView.show(LoadingIndicatorView.SPINNER_WITH_DIMMED_BACKGROUND, true);
        } else {
            progressView.hide();
        }
    }

    @Override
    public void abortAddAccount() {
        enableProgress(true);
        ZMessaging.currentAccounts().fallbackToLastAccount(new AccountsService.SwapAccountCallback() {
            @Override
            public void onSwapComplete() {
                enableProgress(false);
                onEnterApplication(true);
            }

            @Override
            public void onSwapFailed() {
                enableProgress(false);
            }
        });
    }

    @Override
    public void onOpenUrl(String url) {
        try {
            if (!url.startsWith(HTTP_PREFIX) && !url.startsWith(HTTPS_PREFIX)) {
                url = HTTP_PREFIX + url;
            }
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browserIntent);
        } catch (Exception e) {
            Timber.e("Failed to open URL: %s", url);
        }
    }

    @Override
    public int getAccentColor() {
        return accentColor;
    }

    @Override
    public void onShowPhoneInvitationPage() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content,
                     PhoneInvitationFragment.newInstance(getStoreFactory().appEntryStore().getInvitationName(),
                                                         getStoreFactory().appEntryStore().getInvitationPhone()),
                     PhoneInvitationFragment.TAG)
            .commit();
        enableProgress(false);
    }

    @Override
    public void onShowEmailInvitationPage() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content,
                     EmailInvitationFragment.newInstance(getStoreFactory().appEntryStore().getInvitationName(),
                                                         getStoreFactory().appEntryStore().getInvitationEmail()),
                     EmailInvitationFragment.TAG)
            .commit();
        enableProgress(false);
    }

    @Override
    public void onInvitationFailed() {
        Toast.makeText(this, getString(R.string.invitation__email__failed), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInvitationSuccess() {
        getControllerFactory().getUserPreferencesController().setPersonalInvitationToken(null);
    }

    @Override
    public void onShowPhoneRegistrationPage() {
        if (isPaused) {
            return;
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, PhoneRegisterFragment.newInstance(), PhoneRegisterFragment.TAG)
            .commit();
        enableProgress(false);

        if (fromGenericInvite()) {
            getStoreFactory().appEntryStore().setRegistrationContext(RegistrationEventContext.GENERIC_INVITE_PHONE);

            // Temporary tracking to check on high number of invite registrations AN-4117
            String referralToken = getControllerFactory().getUserPreferencesController().getReferralToken();
            String token = getControllerFactory().getUserPreferencesController().getGenericInvitationToken();
            injectJava(GlobalTrackingController.class).tagEvent(new OpenedPhoneRegistrationFromInviteEvent(
                referralToken,
                token));
        } else {
            getStoreFactory().appEntryStore().setRegistrationContext(RegistrationEventContext.PHONE);
        }

        getControllerFactory().getNavigationController().setLeftPage(Page.PHONE_REGISTRATION, TAG);
    }

    @Override
    public void onShowPhoneSignInPage() {

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, PhoneSignInFragment.newInstance(), PhoneSignInFragment.TAG)
            .commit();
        enableProgress(false);

        getControllerFactory().getNavigationController().setLeftPage(Page.PHONE_LOGIN, TAG);
    }

    @Override
    public void onShowPhoneCodePage() {
        if (isPaused) {
            return;
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, VerifyPhoneFragment.newInstance(false), VerifyPhoneFragment.TAG)
            .commit();
        enableProgress(false);

        getControllerFactory().getNavigationController().setLeftPage(Page.PHONE_REGISTRATION_VERIFY_CODE, TAG);
    }

    @Override
    public void onShowPhoneAddEmailPage() {
        if (isPaused) {
            return;
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, OTRPhoneAddEmailFragment.newInstance(), OTRPhoneAddEmailFragment.TAG)
            .addToBackStack(OTRPhoneAddEmailFragment.TAG)
            .commit();
        enableProgress(false);
    }

    @Override
    public void onShowPhoneVerifyEmailPage() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, PhoneVerifyEmailFragment.newInstance(), PhoneVerifyEmailFragment.TAG)
            .commit();
        enableProgress(false);
    }

    @Override
    public void onShowPhoneSetPicturePage() {
        if (getSupportFragmentManager().findFragmentByTag(SignUpPhotoFragment.TAG) != null) {
            return;
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content,
                     SignUpPhotoFragment.newInstance(SignUpPhotoFragment.RegistrationType.Phone),
                     SignUpPhotoFragment.TAG)
            .commit();
        enableProgress(false);

        getControllerFactory().getNavigationController().setLeftPage(Page.PHONE_REGISTRATION_ADD_PHOTO, TAG);
    }

    @Override
    public void onShowEmailWelcomePage() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, WelcomeEmailFragment.newInstance(), WelcomeEmailFragment.TAG)
            .commit();
        enableProgress(false);
        KeyboardUtils.closeKeyboardIfShown(this);
    }

    @Override
    public void onShowEmailRegistrationPage() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content,
                     EmailRegisterFragment.newInstance(),
                     EmailRegisterFragment.TAG)
            .commit();
        enableProgress(false);

        if (fromGenericInvite()) {
            getStoreFactory().appEntryStore().setRegistrationContext(RegistrationEventContext.GENERIC_INVITE_EMAIL);
        } else {
            getStoreFactory().appEntryStore().setRegistrationContext(RegistrationEventContext.EMAIL);
        }
    }

    @Override
    public void onShowEmailVerifyEmailPage() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, EmailVerifyEmailFragment.newInstance(), EmailVerifyEmailFragment.TAG)
            .commit();
        enableProgress(false);
    }

    @Override
    public void onShowEmailSignInPage() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, EmailSignInFragment.newInstance(), EmailSignInFragment.TAG)
            .commit();
        enableProgress(false);

        getControllerFactory().getNavigationController().setLeftPage(Page.EMAIL_LOGIN, TAG);
    }

    @Override
    public void onShowEmailSetPicturePage() {
        if (getSupportFragmentManager().findFragmentByTag(SignUpPhotoFragment.TAG) != null) {
            return;
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content,
                     SignUpPhotoFragment.newInstance(SignUpPhotoFragment.RegistrationType.Email),
                     SignUpPhotoFragment.TAG)
            .commit();
        enableProgress(false);
    }

    @Override
    public void onShowFirstLaunchPage() {
        String id = getStoreFactory().appEntryStore().getUserId();
        boolean hasUserLoggedIn = getControllerFactory().getUserPreferencesController().hasUserLoggedIn(id);
        if (id != null && hasUserLoggedIn) {
            getStoreFactory().appEntryStore().setState(AppEntryState.LOGGED_IN);
        } else {
            if (id != null) {
                getControllerFactory().getUserPreferencesController().userLoggedIn(id);
            }
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            setDefaultAnimation(transaction)
                .replace(R.id.fl_main_content,
                         FirstLaunchAfterLoginFragment.newInstance(),
                         FirstLaunchAfterLoginFragment.TAG)
                .commit();
            enableProgress(false);
        }
    }

    @Override
    public void tagAppEntryEvent(Event event) {
        injectJava(GlobalTrackingController.class).tagEvent(event);
        if (event instanceof SucceededWithRegistrationEvent) {
            Localytics.setProfileAttribute(Attribute.REGISTRATION_WEEK.name(), ZTimeFormatter.getCurrentWeek(this), Localytics.ProfileScope.APPLICATION);
        }
    }

    @Override
    public void onShowPhoneNamePage() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        setDefaultAnimation(transaction)
            .replace(R.id.fl_main_content, PhoneSetNameFragment.newInstance(), PhoneSetNameFragment.TAG)
            .commit();
        enableProgress(false);

        getControllerFactory().getNavigationController().setLeftPage(Page.PHONE_REGISTRATION_ADD_NAME, TAG);
    }

    @Override
    public void onEnterApplication(boolean openSettings) {
        getControllerFactory().getNavigationController().removeNavigationControllerObserver(this);
        getControllerFactory().getVerificationController().finishVerification();
        Intent intent = new Intent(this, MainActivity.class);
        Bundle bundle = new Bundle();
        bundle.putBoolean(MainActivity.OpenSettingsArg(), openSettings);
        intent.putExtras(bundle);
        startActivity(intent);
        finish();
    }

    private FragmentTransaction setDefaultAnimation(FragmentTransaction transaction) {
        transaction.setCustomAnimations(R.anim.new_reg_in,
                                        R.anim.new_reg_out);
        return transaction;
    }

    @Override
    public void openCountryBox() {
        getSupportFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
            .add(R.id.container__country_box, new CountryDialogFragment(), CountryDialogFragment.TAG)
            .addToBackStack(CountryDialogFragment.TAG)
            .commit();

        KeyboardUtils.hideKeyboard(this);
    }

    //////////////////
    //
    // Lifecycle & Store/Controller stuff
    //
    //////////////////
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        getStoreFactory().appEntryStore().onRestoreInstanceState(savedInstanceState,
                                                                    getStoreFactory().zMessagingApiStore().getApi().getSelf());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        getStoreFactory().appEntryStore().onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }


    @Override
    public void onOpenUrlInApp(String url, boolean withCloseButton) {
        if (!url.startsWith(HTTP_PREFIX) && !url.startsWith(HTTPS_PREFIX)) {
            url = HTTP_PREFIX + url;
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.new_reg_in,
                                        R.anim.new_reg_out);
        transaction.add(R.id.fl_main_web_view,
                        InAppWebViewFragment.newInstance(url, withCloseButton),
                        InAppWebViewFragment.TAG);
        transaction.addToBackStack(InAppWebViewFragment.TAG);
        transaction.commit();

        KeyboardUtils.hideKeyboard(this);
    }

    @Override
    public void dismissInAppWebView() {
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Override
    public CountryController getCountryController() {
        return countryController;
    }

    @Override
    public void dismissCountryBox() {
        getSupportFragmentManager().popBackStackImmediate();
        KeyboardUtils.showKeyboard(this);
    }

    //////////////////
    //
    // ZmessagingApiStoreObserver
    //
    //////////////////

    @Override
    public void onForceClientUpdate() {
        startActivity(new Intent(this, ForceUpdateActivity.class));
        finish();
    }

    @Override
    public void onInitialized(Self self) { }

    @Override
    public void onLogout() { }

    private boolean fromGenericInvite() {
        String referralToken = getControllerFactory().getUserPreferencesController().getReferralToken();
        String token = getControllerFactory().getUserPreferencesController().getGenericInvitationToken();
        return token != null || AppEntryStore.GENERAL_GENERIC_INVITE_TOKEN.equals(referralToken);
    }

    @Override
    public ImageAsset getUnsplashImageAsset() {
        return unsplashInitImageAsset;
    }

    @Override
    public void onPageVisible(Page page) {
        switch (page) {
            case PHONE_REGISTRATION:
                injectJava(GlobalTrackingController.class).onApplicationScreen(ApplicationScreen.PHONE_REGISTRATION);
                break;
            case PHONE_REGISTRATION_VERIFY_CODE:
                injectJava(GlobalTrackingController.class).onApplicationScreen(ApplicationScreen.PHONE_REGISTRATION__VERIFY_CODE);
                break;
            case PHONE_REGISTRATION_ADD_NAME:
                injectJava(GlobalTrackingController.class).onApplicationScreen(ApplicationScreen.PHONE_REGISTRATION__ADD_NAME);
                break;
            case PHONE_REGISTRATION_ADD_PHOTO:
                injectJava(GlobalTrackingController.class).onApplicationScreen(ApplicationScreen.PHONE_REGISTRATION__ADD_PHOTO);
                break;
            case EMAIL_LOGIN:
                injectJava(GlobalTrackingController.class).onApplicationScreen(ApplicationScreen.EMAIL_LOGIN);
                break;
            case PHONE_LOGIN:
                injectJava(GlobalTrackingController.class).onApplicationScreen(ApplicationScreen.PHONE_LOGIN);
                break;
        }
    }

}
