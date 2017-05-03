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
package com.waz.zclient.core.api.scala;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.waz.api.AccentColor;
import com.waz.api.CoreList;
import com.waz.api.Credentials;
import com.waz.api.CredentialsFactory;
import com.waz.api.CredentialsUpdateListener;
import com.waz.api.ErrorResponse;
import com.waz.api.ErrorsList;
import com.waz.api.ImageAsset;
import com.waz.api.InvitationTokenFactory;
import com.waz.api.Invitations;
import com.waz.api.KindOfAccess;
import com.waz.api.KindOfVerification;
import com.waz.api.LoginListener;
import com.waz.api.OtrClient;
import com.waz.api.Self;
import com.waz.api.UpdateListener;
import com.waz.api.ZMessagingApi;
import com.waz.content.AccountsStorage;
import com.waz.service.BackendConfig;
import com.waz.service.GlobalModule;
import com.waz.zclient.core.controllers.tracking.attributes.OutcomeAttribute;
import com.waz.zclient.core.controllers.tracking.attributes.RegistrationEventContext;
import com.waz.zclient.core.controllers.tracking.events.registration.EditSelfUser;
import com.waz.zclient.core.controllers.tracking.events.registration.EmailVerification;
import com.waz.zclient.core.controllers.tracking.events.registration.EnteredEmailAndPasswordEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.EnteredNameEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.EnteredPhoneEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.OpenedEmailSignUpEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.OpenedPhoneSignUpEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.PhoneVerification;
import com.waz.zclient.core.controllers.tracking.events.registration.RequestedPhoneVerificationCallEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.ResentEmailVerificationEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.ResentPhoneVerificationEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.SucceededWithRegistrationEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.VerifiedEmailEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.VerifiedPhoneEvent;
import com.waz.zclient.core.controllers.tracking.events.session.LoggedInEvent;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.core.stores.appentry.AppEntryState;
import com.waz.zclient.core.stores.appentry.AppEntryStateCallback;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.utils.BackendConfigStore;
import com.waz.zclient.utils.LayoutSpec;

import java.util.concurrent.TimeUnit;

import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import timber.log.Timber;

public class AppEntryStore implements IAppEntryStore, ErrorsList.ErrorListener {
    public static final String TAG = AppEntryStore.class.getName();
    public static final String GENERAL_GENERIC_INVITE_TOKEN = "getwire";
    private static final String PREF_REGISTRATION = "PREF_REGISTRATION";
    private static final String PREF_ITEM_ENTRY_POINT = "PREF_ITEM_ENTRY_POINT";
    private static final String PREF_ITEM_EMAIL = "PREF_ITEM_EMAIL";
    private static final String PREF_ITEM_COUNTRY_CODE = "PREF_ITEM_COUNTRY_CODE";
    private static final String PREF_ITEM_PHONE_VERIFICATION_CODE = "PREF_ITEM_PHONE_VERIFICATION_CODE";
    private static final String PREF_ITEM_PHONE = "PREF_ITEM_PHONE";
    private static final String PREF_ITEM_NAME = "PREF_ITEM_NAME";
    private static final String SAVED_INSTANCE_CURRENT_STATE = "SAVED_INSTANCE_CURRENT_STATE";

    private Context context;
    private Self self;
    private CoreList<OtrClient> otherOtrClients;
    private ZMessagingApi zMessagingApi;
    private ErrorsList errors;
    private AppEntryStateCallback appEntryStateCallback;
    private AppEntryState currentState;
    private AppEntryState entryPoint;
    private boolean ignoreSelfUpdates;

    private String countryCode;
    private String phone;
    private String phoneVerificationCode;
    private String name;
    private String email;
    private String password;

    private String invitationEmail;
    private String invitationName;
    private String invitationPhone;
    private Invitations.PersonalToken invitationToken;
    private RegistrationEventContext registrationEventContext;

    private boolean isFirstSignInOnDevice = true;

    private UpdateListener selfUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            onSelfUpdated();
        }
    };

    private UpdateListener otrClientsUpdateListender = new UpdateListener() {
        @Override
        public void updated() {
            Timber.i("otrClientsUpdateListender size=%d", otherOtrClients.size());
        }
    };

    public AppEntryStore(Context context, ZMessagingApi zMessagingApi) {
        this.context = context;
        this.zMessagingApi = zMessagingApi;
        errors = zMessagingApi.getErrors();
        errors.addErrorListener(this);
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE);
        if (sharedPreferences.contains(PREF_ITEM_ENTRY_POINT)) {
            entryPoint = AppEntryState.values()[sharedPreferences.getInt(PREF_ITEM_ENTRY_POINT, -1)];
        }

        countryCode = sharedPreferences.getString(PREF_ITEM_COUNTRY_CODE, null);
        phone = sharedPreferences.getString(PREF_ITEM_PHONE, null);
        email = sharedPreferences.getString(PREF_ITEM_EMAIL, null);
        name = sharedPreferences.getString(PREF_ITEM_NAME, null);
        phoneVerificationCode = sharedPreferences.getString(PREF_ITEM_PHONE_VERIFICATION_CODE, null);
    }

    public void resumeAppEntry(Self self, String personalInvitationToken) {

        if (!bindSelf(self)) {
            return;
        }

        if (!TextUtils.isEmpty(personalInvitationToken)) {
            invitationToken = InvitationTokenFactory.personalTokenFromCode(personalInvitationToken);
            zMessagingApi.getInvitations().retrieveInvitationDetails(
                invitationToken,
                new Invitations.InvitationDetailsCallback() {
                    @Override
                    public void onEmailAdressRetrieved(String name, String email) {
                        invitationName = name;
                        invitationEmail = email;
                        setState(AppEntryState.EMAIL_INVITATION);
                    }

                    @Override
                    public void onPhoneNumberRetrieved(String name, String phone) {
                        invitationName = name;
                        invitationPhone = phone;
                        setState(AppEntryState.PHONE_INVITATION);
                    }

                    @Override
                    public void onRetrievalFailed(ErrorResponse errorResponse) {
                        if (LayoutSpec.isPhone(context)) {
                            setState(AppEntryState.PHONE_REGISTER);
                        } else {
                            setState(AppEntryState.EMAIL_WELCOME);
                        }
                    }
                });
            return;
        }

        // Resume phone registration
        if (entryPoint == AppEntryState.PHONE_REGISTER && self.isLoggedIn()) {
            if (self.getPicture().isEmpty()) {
                setState(AppEntryState.PHONE_SET_PICTURE);
                return;
            }
            setState(AppEntryState.LOGGED_IN);
            return;
        }

        //Resume at phone set name
        if (entryPoint == AppEntryState.PHONE_SET_NAME && self.isLoggedIn()) {
            setState(AppEntryState.PHONE_SET_NAME);
            return;
        }

        // Resume phone sign-in
        if (entryPoint == AppEntryState.PHONE_SIGN_IN && self.isLoggedIn()) {
            setState(AppEntryState.PHONE_SIGNED_IN_RESUMING);
            return;
        }

        // Resume email registration
        if (entryPoint == AppEntryState.EMAIL_REGISTER) {
            if (self.isLoggedIn() && !self.accountActivated()) {
                setState(AppEntryState.EMAIL_VERIFY_EMAIL);
                return;
            }

            if (self.accountActivated() && self.getPicture().isEmpty()) {
                setState(AppEntryState.EMAIL_SET_PICTURE);
                return;
            }

            if (self.accountActivated()) {
                appEntryStateCallback.tagAppEntryEvent(new VerifiedEmailEvent(OutcomeAttribute.SUCCESS, "", getEmailRegistrationContext()));
                appEntryStateCallback.tagAppEntryEvent(new SucceededWithRegistrationEvent(getEmailRegistrationContext()));
                setState(AppEntryState.LOGGED_IN);
                return;
            }
        }

        // Resume email sign-in
        if (entryPoint == AppEntryState.EMAIL_SIGN_IN && self.isLoggedIn()) {
            setState(AppEntryState.EMAIL_SIGNED_IN);
            return;
        }

        // Start registration
        if (LayoutSpec.isPhone(context)) {
            setState(AppEntryState.PHONE_REGISTER);
        } else {
            setState(AppEntryState.EMAIL_WELCOME);
        }
    }

    @Override
    public void clearCurrentState() {
        bindSelf(null);
        currentState = null;
    }

    @Override
    public void clearSavedUserInput() {
        countryCode = null;
        phone = null;
        name = null;
        email = null;
        password = null;
        phoneVerificationCode = null;

        context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE)
               .edit()
               .remove(PREF_ITEM_COUNTRY_CODE)
               .remove(PREF_ITEM_PHONE)
               .remove(PREF_ITEM_NAME)
               .remove(PREF_ITEM_EMAIL)
               .remove(PREF_ITEM_PHONE_VERIFICATION_CODE)
               .apply();
    }

    // Here we handle email verification click on a different device
    private void onSelfUpdated() {
        if (otherOtrClients == null || otherOtrClients.size() == 0) {
            if (otherOtrClients != null) {
                otherOtrClients.removeUpdateListener(otrClientsUpdateListender);
            }
            otherOtrClients = self.getOtherOtrClients();
            otherOtrClients.addUpdateListener(otrClientsUpdateListender);
        }
        if (ignoreSelfUpdates || appEntryStateCallback == null) {
            return;
        }
        if  (currentState == AppEntryState.PHONE_EMAIL_PASSWORD) {
            return;
        }
        if (entryPoint == AppEntryState.PHONE_SIGN_IN && self.accountActivated()) {
            appEntryStateCallback.tagAppEntryEvent(EmailVerification.success(EmailVerification.Context.POST_LOGIN));
            if (self.getPicture().isEmpty()) {
                setState(AppEntryState.PHONE_SET_PICTURE);
                return;
            }
            setState(AppEntryState.LOGGED_IN);
            return;
        }

        if (entryPoint == AppEntryState.EMAIL_REGISTER && self.accountActivated()) {
            if (currentState == AppEntryState.EMAIL_VERIFY_EMAIL) {
                // Check state so following registration events are triggered only once
                appEntryStateCallback.tagAppEntryEvent(new VerifiedEmailEvent(OutcomeAttribute.SUCCESS,
                                                                              "",
                                                                              getEmailRegistrationContext()));
                appEntryStateCallback.tagAppEntryEvent(new SucceededWithRegistrationEvent(getEmailRegistrationContext()));
            }
            if (self.getPicture().isEmpty()) {
                setState(AppEntryState.EMAIL_SET_PICTURE);
                return;
            }
            setState(AppEntryState.LOGGED_IN);
        }
    }

    /* returns true if bound to new self */
    private boolean bindSelf(Self self) {
        // always unregister old self
        if (this.self != null &&
            this.self != self) {
            this.self.removeUpdateListener(selfUpdateListener);
            if (otherOtrClients != null) {
                otherOtrClients.removeUpdateListener(otrClientsUpdateListender);
            }
        } else if (this.self == self) {
            return false;
        }

        if (self == null) {
            this.self = null;
            otherOtrClients = null;
            return false;
        }

        this.self = self;
        this.self.addUpdateListener(selfUpdateListener);
        otherOtrClients = self.getOtherOtrClients();
        otherOtrClients.addUpdateListener(otrClientsUpdateListender);
        return true;
    }

    ////////////////////////
    // State Machine
    ////////////////////////

    @Override
    public boolean onBackPressed() {
        if (currentState == null) {
            return false;
        }

        switch (currentState) {

            case PHONE_SIGN_IN:
            case PHONE_INVITATION:
            case EMAIL_INVITATION:
                if (LayoutSpec.isPhone(context)) {
                    setState(AppEntryState.PHONE_REGISTER);
                } else {
                    setState(AppEntryState.EMAIL_WELCOME);
                }
                return true;
            case PHONE_SET_CODE:
                if (entryPoint == AppEntryState.EMAIL_SIGN_IN) {
                    setState(AppEntryState.EMAIL_SET_PHONE);
                } else {
                    setState(entryPoint);
                }
                return true;
            case PHONE_VERIFY_EMAIL:
                setState(AppEntryState.PHONE_EMAIL_PASSWORD);
                return true;
            case EMAIL_VERIFY_EMAIL:
                setState(AppEntryState.EMAIL_REGISTER);
                return true;
            case EMAIL_REGISTER:
                setState(AppEntryState.EMAIL_WELCOME);
                return true;
            case EMAIL_SIGN_IN:
                if (LayoutSpec.isPhone(context)) {
                    setState(AppEntryState.PHONE_REGISTER);
                } else {
                    setState(AppEntryState.EMAIL_WELCOME);
                }
                return true;
            case EMAIL_SET_CODE:
                setState(AppEntryState.EMAIL_SET_PHONE);
                return true;
            case PHONE_SET_PICTURE:
                setState(AppEntryState.PHONE_SET_NAME);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void tearDown() {
        bindSelf(null);
        errors.removeErrorListener(this);
        errors = null;
        zMessagingApi = null;
        context = null;
    }

    @Override
    public void setState(AppEntryState state) {
        setStateInternal(state, false);
    }

    @Override
    public void triggerStateUpdate() {
        if (currentState == null) {
            return;
        }
        setStateInternal(currentState, true);
    }

    private void setStateInternal(AppEntryState state, boolean forceUpdate) {
        if (appEntryStateCallback == null) {
            return;
        }

        if (currentState == state && !forceUpdate) {
            return;
        }

        currentState = state;

        if (AppEntryState.entryPoints().contains(state)) {
            persistAppEntryPoint(state);
        }

        switch (state) {
            case PHONE_REGISTER:
                appEntryStateCallback.onShowPhoneRegistrationPage();
                break;
            case PHONE_SIGN_IN:
                appEntryStateCallback.onShowPhoneSignInPage();
                break;
            case PHONE_SET_CODE:
                appEntryStateCallback.onShowPhoneCodePage();
                break;
            case PHONE_SIGNED_IN:
                if (self.getEmail().isEmpty() && otherOtrClients != null && otherOtrClients.size() > 0) {
                    setState(AppEntryState.PHONE_EMAIL_PASSWORD);
                    break;
                }
                if (self.getPicture().isEmpty()) {
                    setState(AppEntryState.PHONE_SET_PICTURE);
                    break;
                }
                setState(AppEntryState.LOGGED_IN);
                break;
            // TODO: This state was needed because SyncEngine isEmailVerified() was not entirely reliable,
            // accountActivated() flag should not have this problem, we should remove this special state.
            case PHONE_SIGNED_IN_RESUMING:
                if (self.getEmail().isEmpty() && otherOtrClients != null && otherOtrClients.size() > 0) {
                    setState(AppEntryState.PHONE_EMAIL_PASSWORD);
                    break;
                }
                if (!self.accountActivated()) {
                    setState(AppEntryState.PHONE_VERIFY_EMAIL);
                    break;
                }
                if (self.getPicture().isEmpty()) {
                    setState(AppEntryState.PHONE_SET_PICTURE);
                    break;
                }
                setState(AppEntryState.FIRST_LOGIN);
                break;
            case PHONE_EMAIL_PASSWORD:
                appEntryStateCallback.onShowPhoneAddEmailPage();
                break;
            case PHONE_VERIFY_EMAIL:
                appEntryStateCallback.onShowPhoneVerifyEmailPage();
                break;
            case PHONE_SET_NAME:
                appEntryStateCallback.onShowPhoneNamePage();
                break;
            case PHONE_SET_PICTURE:
                appEntryStateCallback.onShowPhoneSetPicturePage();
                break;
            case EMAIL_WELCOME:
                appEntryStateCallback.onShowEmailWelcomePage();
                break;
            case EMAIL_REGISTER:
                appEntryStateCallback.onShowEmailRegistrationPage();
                break;
            case EMAIL_SIGN_IN:
                appEntryStateCallback.onShowEmailSignInPage();
                break;
            case EMAIL_VERIFY_EMAIL:
                appEntryStateCallback.onShowEmailVerifyEmailPage();
                break;
            case EMAIL_SET_PICTURE:
                appEntryStateCallback.onShowEmailSetPicturePage();
                break;
            case EMAIL_SET_PHONE:
                appEntryStateCallback.onShowEmailAddPhonePage();
                break;
            case EMAIL_SET_CODE:
                appEntryStateCallback.onShowEmailPhoneCodePage();
                break;
            case EMAIL_SIGNED_IN:
                if (!self.accountActivated()) {
                    setState(AppEntryState.EMAIL_VERIFY_EMAIL);
                    break;
                }
                if (self.getPicture().isEmpty()) {
                    setState(AppEntryState.EMAIL_SET_PICTURE);
                    break;
                }
                if (self.getPhone().isEmpty()) {
                    setState(AppEntryState.EMAIL_SET_PHONE);
                    break;
                }
                setState(AppEntryState.FIRST_LOGIN);
                break;
            case LOGGED_IN:
                entryPoint = null;
                context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE).edit().clear().apply();
                self.removeUpdateListener(selfUpdateListener);

                if (otherOtrClients != null) {
                    otherOtrClients.removeUpdateListener(otrClientsUpdateListender);
                }
                appEntryStateCallback.onEnterApplication();
                break;
            case EMAIL_INVITATION:
                appEntryStateCallback.onShowEmailInvitationPage();
                setRegistrationContext(RegistrationEventContext.PERSONAL_INVITE_EMAIL);
                break;
            case PHONE_INVITATION:
                appEntryStateCallback.onShowPhoneInvitationPage();
                setRegistrationContext(RegistrationEventContext.PERSONAL_INVITE_PHONE);
                break;
            case FIRST_LOGIN:
                if (isFirstSignInOnDevice) {
                    appEntryStateCallback.onShowFirstLaunchPage();
                } else {
                    setState(AppEntryState.LOGGED_IN);
                }
                break;
        }
    }

    private void persistAppEntryPoint(AppEntryState state) {
        entryPoint = state;
        context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE)
               .edit()
               .putInt(PREF_ITEM_ENTRY_POINT, entryPoint.ordinal())
               .apply();
    }

    @Override
    public void setCallback(AppEntryStateCallback callback) {
        appEntryStateCallback = callback;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(SAVED_INSTANCE_CURRENT_STATE, currentState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState, Self self) {
        // Shouldn't setState() or resumeAppEntry(), as the correct fragment should already be displayed
        currentState = (AppEntryState) savedInstanceState.getSerializable(SAVED_INSTANCE_CURRENT_STATE);
        bindSelf(self);
    }

    @Override
    public AppEntryState getEntryPoint() {
        return entryPoint;
    }

    ////////////////////////
    //
    // Calls from UI
    //
    ////////////////////////

    @Override
    public void setRegistrationPhone(final String countryCode, final String phone, final ErrorCallback errorCallback) {

        setAndStoreCountryCode(countryCode);
        setAndStorePhone(phone);
        zMessagingApi.requestPhoneConfirmationCode(countryCode + phone,
                                                   KindOfAccess.REGISTRATION,
                                                   new ZMessagingApi.PhoneConfirmationCodeRequestListener() {
                                                       @Override
                                                       public void onConfirmationCodeSent(KindOfAccess kindOfAccess) {
                                                           appEntryStateCallback.tagAppEntryEvent(new EnteredPhoneEvent(
                                                               OutcomeAttribute.SUCCESS,
                                                               "",
                                                               "",
                                                               getPhoneRegistrationContext()));
                                                           setState(AppEntryState.PHONE_SET_CODE);
                                                       }

                                                       @Override
                                                       public void onPasswordExists(KindOfAccess kindOfAccess) {

                                                       }

                                                       @Override
                                                       public void onConfirmationCodeSendingFailed(KindOfAccess kindOfAccess,
                                                                                                   int errorCode,
                                                                                                   String message,
                                                                                                   String label) {
                                                           appEntryStateCallback.tagAppEntryEvent(new EnteredPhoneEvent(
                                                               OutcomeAttribute.FAIL,
                                                               String.valueOf(errorCode),
                                                               message + "; " + label,
                                                               getPhoneRegistrationContext()));
                                                           // This phone number is already registered, redirect to sign-in
                                                           if (errorCode == AppEntryError.PHONE_EXISTS.errorCode) {
                                                               persistAppEntryPoint(AppEntryState.PHONE_SIGN_IN);
                                                               setSignInPhone(countryCode, phone, errorCallback);
                                                               // Pass error to UI
                                                           } else if (AppEntryError.PHONE_INVALID_FORMAT.correspondsTo(errorCode, label)) {
                                                               appEntryStateCallback.tagAppEntryEvent(PhoneVerification.codeRequestErrorReg());
                                                               errorCallback.onError(AppEntryError.PHONE_INVALID_FORMAT);
                                                           } else if (AppEntryError.PHONE_BUDGET_EXHAUSTED.correspondsTo(errorCode, label)) {
                                                               appEntryStateCallback.tagAppEntryEvent(PhoneVerification.codeRequestErrorReg());
                                                               errorCallback.onError(AppEntryError.PHONE_BUDGET_EXHAUSTED);
                                                           } else {
                                                               appEntryStateCallback.tagAppEntryEvent(PhoneVerification.codeRequestErrorReg());
                                                               errorCallback.onError(AppEntryError.PHONE_REGISTER_GENERIC_ERROR);
                                                           }
                                                       }
                                                   });
    }

    @Override
    public void setSignInPhone(final String countryCode, final String phone, final ErrorCallback errorCallback) {
        try {
            AccountsStorage storage = getAccountsStorage();
            isFirstSignInOnDevice = storage == null || !Await.result(storage.findByPhone(phone),
                Duration.create(1, TimeUnit.SECONDS)).isDefined();
        } catch (Exception e) {
            isFirstSignInOnDevice = true;
        }

        setAndStoreCountryCode(countryCode);
        setAndStorePhone(phone);
        zMessagingApi.requestPhoneConfirmationCode(countryCode + phone,
                                                   KindOfAccess.LOGIN,
                                                   new ZMessagingApi.PhoneConfirmationCodeRequestListener() {
                                                       @Override
                                                       public void onConfirmationCodeSent(KindOfAccess kindOfAccess) {
                                                           setState(AppEntryState.PHONE_SET_CODE);
                                                       }

                                                       @Override
                                                       public void onPasswordExists(KindOfAccess kindOfAccess) {

                                                       }

                                                       @Override
                                                       public void onConfirmationCodeSendingFailed(KindOfAccess kindOfAccess,
                                                                                                   int errorCode,
                                                                                                   String message,
                                                                                                   String label) {

                                                           if (AppEntryError.PHONE_INVALID_FORMAT.correspondsTo(errorCode, label)) {
                                                               errorCallback.onError(AppEntryError.PHONE_INVALID_FORMAT);
                                                               // Recently sent SMS, take user to phoneVerificationCode page
                                                           } else if (AppEntryError.PHONE_PENDING_LOGIN.correspondsTo(errorCode, label)) {
                                                               setState(AppEntryState.PHONE_SET_CODE);
                                                               // This phone number was never registered, redirect to registration
                                                           } else if (AppEntryError.PHONE_INVALID.correspondsTo(errorCode, label)) {
                                                               persistAppEntryPoint(AppEntryState.PHONE_REGISTER);
                                                               setRegistrationPhone(countryCode, phone, errorCallback);
                                                               // Pass error to UI
                                                           } else if (AppEntryError.PHONE_BUDGET_EXHAUSTED.correspondsTo(errorCode, label)) {
                                                               appEntryStateCallback.tagAppEntryEvent(PhoneVerification.codeRequestErrorSignIn());
                                                               errorCallback.onError(AppEntryError.PHONE_BUDGET_EXHAUSTED);
                                                           } else {
                                                               appEntryStateCallback.tagAppEntryEvent(PhoneVerification.codeRequestErrorSignIn());
                                                               errorCallback.onError(AppEntryError.LOGIN_GENERIC_ERROR);
                                                           }
                                                       }
                                                   });
    }

    @Override
    public void triggerVerificationCodeCallToUser(final SuccessCallback successCallback, final ErrorCallback errorCallback) {
        final boolean isEntryPointPhoneRegister = entryPoint == AppEntryState.PHONE_REGISTER;
        boolean isEntryPointPhoneSignIn = entryPoint == AppEntryState.PHONE_SIGN_IN;

        KindOfAccess kindOfAccess = KindOfAccess.REGISTRATION;
        if (isEntryPointPhoneSignIn) {
            kindOfAccess = KindOfAccess.LOGIN;
        }

        zMessagingApi.requestPhoneConfirmationCall(countryCode + phone,
                                                   kindOfAccess,
                                                   new ZMessagingApi.PhoneConfirmationCodeRequestListener() {
                                                       @Override
                                                       public void onConfirmationCodeSent(KindOfAccess kindOfAccess) {
                                                           if (isEntryPointPhoneRegister) {
                                                               appEntryStateCallback.tagAppEntryEvent(new RequestedPhoneVerificationCallEvent(OutcomeAttribute.SUCCESS, "", getPhoneRegistrationContext()));

                                                           }
                                                           successCallback.onSuccess();
                                                       }

                                                       @Override
                                                       public void onPasswordExists(KindOfAccess kindOfAccess) {

                                                       }

                                                       @Override
                                                       public void onConfirmationCodeSendingFailed(KindOfAccess kindOfAccess,
                                                                                                   int errorCode,
                                                                                                   String message,
                                                                                                   String label) {
                                                           if (isEntryPointPhoneRegister) {
                                                               appEntryStateCallback.tagAppEntryEvent(new RequestedPhoneVerificationCallEvent(OutcomeAttribute.FAIL, "", getPhoneRegistrationContext()));

                                                           }
                                                           if (AppEntryError.PHONE_BUDGET_EXHAUSTED.correspondsTo(errorCode, label)) {
                                                               errorCallback.onError(AppEntryError.PHONE_BUDGET_EXHAUSTED);
                                                           } else {
                                                               errorCallback.onError(AppEntryError.PHONE_REGISTER_GENERIC_ERROR);
                                                           }
                                                       }
                                                   });
    }

    @Override
    public void resendPhone(final SuccessCallback successCallback, final ErrorCallback errorCallback) {

        boolean isEntryPointPhoneSignIn = entryPoint == AppEntryState.PHONE_SIGN_IN;
        final boolean isEntryPointPhoneRegister = entryPoint == AppEntryState.PHONE_REGISTER;
        boolean isEntryPointEmailSignIn = entryPoint == AppEntryState.EMAIL_SIGN_IN;

        if (isEntryPointPhoneSignIn) {
            zMessagingApi.requestPhoneConfirmationCode(countryCode + phone,
                                                       KindOfAccess.LOGIN,
                                                       new ZMessagingApi.PhoneConfirmationCodeRequestListener() {
                                                           @Override
                                                           public void onConfirmationCodeSent(KindOfAccess kindOfAccess) {
                                                               appEntryStateCallback.tagAppEntryEvent(PhoneVerification.resent(PhoneVerification.Context.SIGN_IN));
                                                               successCallback.onSuccess();
                                                           }

                                                           @Override
                                                           public void onPasswordExists(KindOfAccess kindOfAccess) {

                                                           }

                                                           @Override
                                                           public void onConfirmationCodeSendingFailed(KindOfAccess kindOfAccess,
                                                                                                       int errorCode,
                                                                                                       String message,
                                                                                                       String label) {
                                                               if (AppEntryError.PHONE_PENDING_LOGIN.correspondsTo(
                                                                   errorCode,
                                                                   label)) {
                                                                   errorCallback.onError(AppEntryError.PHONE_PENDING_LOGIN);
                                                               } else if (AppEntryError.PHONE_BUDGET_EXHAUSTED.correspondsTo(errorCode, label)) {
                                                                   errorCallback.onError(AppEntryError.PHONE_BUDGET_EXHAUSTED);
                                                               } else {
                                                                   errorCallback.onError(AppEntryError.LOGIN_GENERIC_ERROR);
                                                               }
                                                           }
                                                       });
        } else if (isEntryPointPhoneRegister || isEntryPointEmailSignIn) {
            zMessagingApi.requestPhoneConfirmationCode(countryCode + phone,
                                                       KindOfAccess.REGISTRATION,
                                                       new ZMessagingApi.PhoneConfirmationCodeRequestListener() {
                                                           @Override
                                                           public void onConfirmationCodeSent(KindOfAccess kindOfAccess) {
                                                               if (isEntryPointPhoneRegister) {
                                                                   appEntryStateCallback.tagAppEntryEvent(new ResentPhoneVerificationEvent(OutcomeAttribute.SUCCESS, "", getPhoneRegistrationContext()));

                                                               } else {
                                                                   appEntryStateCallback.tagAppEntryEvent(PhoneVerification.resent(PhoneVerification.Context.POST_LOGIN));
                                                               }
                                                               successCallback.onSuccess();
                                                           }

                                                           @Override
                                                           public void onPasswordExists(KindOfAccess kindOfAccess) {

                                                           }

                                                           @Override
                                                           public void onConfirmationCodeSendingFailed(KindOfAccess kindOfAccess,
                                                                                                       int errorCode,
                                                                                                       String message,
                                                                                                       String label) {
                                                               if (isEntryPointPhoneRegister) {
                                                                   appEntryStateCallback.tagAppEntryEvent(new ResentPhoneVerificationEvent(OutcomeAttribute.FAIL, String.valueOf(errorCode), registrationEventContext));
                                                               } else {
                                                                   appEntryStateCallback.tagAppEntryEvent(PhoneVerification.codeRequestErrorSignIn());
                                                               }
                                                               if (AppEntryError.PHONE_BUDGET_EXHAUSTED.correspondsTo(errorCode, label)) {
                                                                   errorCallback.onError(AppEntryError.PHONE_BUDGET_EXHAUSTED);
                                                               } else {
                                                                   errorCallback.onError(AppEntryError.PHONE_REGISTER_GENERIC_ERROR);
                                                               }
                                                           }
                                                       });
        }
    }

    @Override
    public void submitCode(String phoneVerificationCode, final ErrorCallback errorCallback) {
        setAndStorePhoneVerificationCode(phoneVerificationCode);
        if (entryPoint == AppEntryState.PHONE_REGISTER) {
            zMessagingApi.verifyPhoneNumber(countryCode + phone,
                                            phoneVerificationCode,
                                            KindOfVerification.PREVERIFY_ON_REGISTRATION,
                                            new ZMessagingApi.PhoneNumberVerificationListener() {
                                                @Override
                                                public void onVerified(KindOfVerification kindOfVerification) {
                                                    appEntryStateCallback.tagAppEntryEvent(new VerifiedPhoneEvent(
                                                        OutcomeAttribute.SUCCESS,
                                                        "",
                                                        "",
                                                        getPhoneRegistrationContext()));
                                                    setState(AppEntryState.PHONE_SET_NAME);
                                                }

                                                @Override
                                                public void onVerificationFailed(KindOfVerification kindOfVerification,
                                                                                 int errorCode,
                                                                                 String message,
                                                                                 String label) {
                                                    appEntryStateCallback.tagAppEntryEvent(new VerifiedPhoneEvent(
                                                        OutcomeAttribute.FAIL,
                                                        String.valueOf(errorCode),
                                                        message + "; " + label,
                                                        getPhoneRegistrationContext()));
                                                    if (AppEntryError.PHONE_INVALID_REGISTRATION_CODE.correspondsTo(errorCode, label)) {
                                                        errorCallback.onError(AppEntryError.PHONE_INVALID_REGISTRATION_CODE);
                                                    } else {
                                                        errorCallback.onError(AppEntryError.PHONE_REGISTER_GENERIC_ERROR);
                                                    }
                                                }
                                            });
        } else if (entryPoint == AppEntryState.PHONE_SIGN_IN) {
            ignoreSelfUpdates = true;
            zMessagingApi.login(CredentialsFactory.phoneCredentials(countryCode + phone, phoneVerificationCode),
                                new LoginListener() {
                                    @Override
                                    public void onSuccess(Self self) {
                                        appEntryStateCallback.tagAppEntryEvent(PhoneVerification.success(PhoneVerification.Context.SIGN_IN));
                                        appEntryStateCallback.tagAppEntryEvent(new LoggedInEvent(true));
                                        bindSelf(self);
                                        setState(AppEntryState.PHONE_SIGNED_IN);
                                        ignoreSelfUpdates = false;
                                    }

                                    @Override
                                    public void onFailed(int errorCode, String message, String label) {
                                        appEntryStateCallback.tagAppEntryEvent(PhoneVerification.error(PhoneVerification.Context.SIGN_IN));
                                        if (AppEntryError.PHONE_INVALID_LOGIN_CODE.correspondsTo(errorCode, "")) {
                                            errorCallback.onError(AppEntryError.PHONE_INVALID_LOGIN_CODE);
                                        } else if (AppEntryError.TOO_MANY_ATTEMPTS.correspondsTo(errorCode, "")) {
                                            errorCallback.onError(AppEntryError.TOO_MANY_ATTEMPTS);
                                        } else {
                                            errorCallback.onError(AppEntryError.LOGIN_GENERIC_ERROR);
                                        }
                                        ignoreSelfUpdates = false;
                                    }
                                });
        } else if (entryPoint == AppEntryState.EMAIL_SIGN_IN) {
            zMessagingApi.verifyPhoneNumber(countryCode + phone,
                                            phoneVerificationCode,
                                            KindOfVerification.VERIFY_ON_UPDATE,
                                            new ZMessagingApi.PhoneNumberVerificationListener() {
                                                @Override
                                                public void onVerified(KindOfVerification kindOfVerification) {
                                                    appEntryStateCallback.tagAppEntryEvent(PhoneVerification.success(PhoneVerification.Context.POST_LOGIN));
                                                    setState(AppEntryState.LOGGED_IN);
                                                }

                                                @Override
                                                public void onVerificationFailed(KindOfVerification kindOfVerification,
                                                                                 int errorCode,
                                                                                 String message,
                                                                                 String label) {
                                                    appEntryStateCallback.tagAppEntryEvent(PhoneVerification.error(PhoneVerification.Context.POST_LOGIN));
                                                    if (AppEntryError.PHONE_INVALID_ADD_CODE.correspondsTo(errorCode,
                                                                                                           label)) {
                                                        errorCallback.onError(AppEntryError.PHONE_INVALID_ADD_CODE);
                                                    } else {
                                                        errorCallback.onError(AppEntryError.LOGIN_GENERIC_ERROR);
                                                    }
                                                }
                                            });
        }
    }

    @Override
    public void registerWithPhone(String name, AccentColor accentColor, final ErrorCallback errorCallback) {
        if (alreadyRegisteredWithPhone()) {
            self.setName(name);
            setState(AppEntryState.PHONE_SET_PICTURE);
        } else {
            zMessagingApi.register(CredentialsFactory.phoneCredentials(countryCode + phone, phoneVerificationCode),
                                   name,
                                   accentColor,
                                   new ZMessagingApi.RegistrationListener() {
                                       @Override
                                       public void onRegistered(Self self) {
                                           appEntryStateCallback.tagAppEntryEvent(new EnteredNameEvent(OutcomeAttribute.SUCCESS, "", getPhoneRegistrationContext()));
                                           appEntryStateCallback.tagAppEntryEvent(new SucceededWithRegistrationEvent(getPhoneRegistrationContext()));
                                           bindSelf(self);
                                           setState(AppEntryState.PHONE_SET_PICTURE);

                                       }

                                       @Override
                                       public void onRegistrationFailed(int errorCode, String message, String label) {
                                           appEntryStateCallback.tagAppEntryEvent(new EnteredNameEvent(OutcomeAttribute.FAIL, String.valueOf(errorCode), getPhoneRegistrationContext()));
                                           if (AppEntryError.PHONE_INVALID_REGISTRATION_CODE.correspondsTo(errorCode,
                                                                                                           label)) {
                                               errorCallback.onError(AppEntryError.PHONE_INVALID_REGISTRATION_CODE);
                                           } else if (AppEntryError.PHONE_EXISTS.correspondsTo(errorCode, label)) {
                                               errorCallback.onError(AppEntryError.PHONE_EXISTS);
                                           } else {
                                               errorCallback.onError(AppEntryError.PHONE_REGISTER_GENERIC_ERROR);
                                           }
                                       }
                                   });
        }
        setAndStoreName(name);
    }

    private boolean alreadyRegisteredWithPhone() {
        //this could be true if the user navigates back
        return self != null && self.isLoggedIn();
    }

    @Override
    public void setPhonePicture(ImageAsset imageAsset) {
        zMessagingApi.getSelf().setPicture(imageAsset);
        setState(AppEntryState.LOGGED_IN);
    }

    @Override
    public void addEmailAndPasswordToPhone(final String email,
                                           final String password,
                                           final ErrorCallback emailErrorCallback,
                                           final ErrorCallback passwordErrorCallback) {
        setAndStoreEmail(email);
        this.password = password;

        final CredentialsUpdateListener emailUpdateListener = new CredentialsUpdateListener() {
            @Override
            public void onUpdated() {
                appEntryStateCallback.tagAppEntryEvent(EmailVerification.submitted(EmailVerification.Context.POST_LOGIN));
                appEntryStateCallback.tagAppEntryEvent(EditSelfUser.emailAddedSignIn());
                setState(AppEntryState.PHONE_VERIFY_EMAIL);
            }

            @Override
            public void onUpdateFailed(int errorCode, String message, String label) {
                if (AppEntryError.EMAIL_EXISTS.correspondsTo(errorCode, label)) {
                    emailErrorCallback.onError(AppEntryError.EMAIL_EXISTS);
                } else if (AppEntryError.EMAIL_INVALID.correspondsTo(errorCode, label)) {
                    emailErrorCallback.onError(AppEntryError.EMAIL_INVALID);
                } else {
                    emailErrorCallback.onError(AppEntryError.EMAIL_GENERIC_ERROR);
                }
            }
        };
        self.setPassword(password, new CredentialsUpdateListener() {
            @Override
            public void onUpdated() {
                appEntryStateCallback.tagAppEntryEvent(EditSelfUser.passwordAddedSignIn());
                self.setEmail(email, emailUpdateListener);
            }

            @Override
            public void onUpdateFailed(int errorCode, String message, String label) {
                // Edge case where password was set on another device while email/pw
                // were being added on this one.
                if (errorCode == AppEntryError.FORBIDDEN.errorCode) {
                    self.setEmail(email, emailUpdateListener);
                } else {
                    passwordErrorCallback.onError(AppEntryError.PHONE_ADD_PASSWORD);
                }
            }
        });
    }

    @Override
    public void setEmailPicture(ImageAsset imageAsset) {
        zMessagingApi.getSelf().setPicture(imageAsset);
        setState(AppEntryState.LOGGED_IN);
    }

    @Override
    public void registerWithEmail(final String email,
                                  final String password,
                                  final String name,
                                  final AccentColor accentColor,
                                  final ErrorCallback errorCallback) {
        setAndStoreEmail(email);
        setAndStoreName(name);
        this.password = password;

        zMessagingApi.register(CredentialsFactory.emailCredentials(email, password),
                               name,
                               accentColor,
                               new ZMessagingApi.RegistrationListener() {
                                   @Override
                                   public void onRegistered(Self self) {
                                       appEntryStateCallback.tagAppEntryEvent(new EnteredEmailAndPasswordEvent(OutcomeAttribute.SUCCESS, "", getEmailRegistrationContext()));
                                       bindSelf(self);
                                       setState(AppEntryState.EMAIL_VERIFY_EMAIL);
                                   }

                                   @Override
                                   public void onRegistrationFailed(int errorCode, String message, String label) {
                                       appEntryStateCallback.tagAppEntryEvent(new EnteredEmailAndPasswordEvent(OutcomeAttribute.FAIL, String.valueOf(errorCode), getEmailRegistrationContext()));
                                       if (AppEntryError.EMAIL_EXISTS.correspondsTo(errorCode, label)) {
                                           errorCallback.onError(AppEntryError.EMAIL_EXISTS);
                                       } else if (AppEntryError.SERVER_CONNECTIVITY_ERROR.correspondsTo(errorCode,
                                                                                                        "")) {
                                           errorCallback.onError(AppEntryError.SERVER_CONNECTIVITY_ERROR);
                                       } else {
                                           errorCallback.onError(AppEntryError.EMAIL_REGISTER_GENERIC_ERROR);
                                       }
                                   }
                               });
    }

    @Override
    public void acceptEmailInvitation(String password, AccentColor accentColor) {
        Credentials credentials = CredentialsFactory.emailInvitationCredentials(invitationEmail, password, invitationToken);
        zMessagingApi.register(credentials, invitationName, accentColor, new ZMessagingApi.RegistrationListener() {
            @Override
            public void onRegistered(Self self) {
                bindSelf(self);
                setState(AppEntryState.EMAIL_SET_PICTURE);
                appEntryStateCallback.onInvitationSuccess();
                appEntryStateCallback.tagAppEntryEvent(new SucceededWithRegistrationEvent(RegistrationEventContext.PERSONAL_INVITE_EMAIL));
            }

            @Override
            public void onRegistrationFailed(int code, String message, String label) {
                Timber.e("Email invitation registration failed");
                appEntryStateCallback.onInvitationFailed();
                if (LayoutSpec.isPhone(context)) {
                    setState(AppEntryState.PHONE_REGISTER);
                } else {
                    setState(AppEntryState.EMAIL_WELCOME);
                }
            }
        });
    }

    @Override
    public void acceptPhoneInvitation(AccentColor accentColor) {
        Credentials credentials = CredentialsFactory.phoneInvitationCredentials(invitationPhone, invitationToken);
        zMessagingApi.register(credentials, invitationName, accentColor, new ZMessagingApi.RegistrationListener() {
            @Override
            public void onRegistered(Self self) {
                bindSelf(self);
                setState(AppEntryState.PHONE_SET_PICTURE);
                appEntryStateCallback.onInvitationSuccess();
                appEntryStateCallback.tagAppEntryEvent(new SucceededWithRegistrationEvent(RegistrationEventContext.PERSONAL_INVITE_PHONE));
            }

            @Override
            public void onRegistrationFailed(int i, String s, String s1) {
                Timber.e("Email invitation registration failed");
                appEntryStateCallback.onInvitationFailed();
                if (LayoutSpec.isPhone(context)) {
                    setState(AppEntryState.PHONE_REGISTER);
                } else {
                    setState(AppEntryState.EMAIL_WELCOME);
                }
            }
        });
    }

    @Nullable
    private AccountsStorage getAccountsStorage() {
        BackendConfig currentBackend = BackendConfigStore.getConfig(context);
        GlobalModule global = new GlobalModule(context, currentBackend);
        return global.accountsStorage();
    }

    @Override
    public void signInWithEmail(String email, String password, final ErrorCallback errorCallback) {
        try {
            AccountsStorage storage = getAccountsStorage();
            isFirstSignInOnDevice = storage == null ||
                !Await.result(storage.findByEmail(email), Duration.create(1, TimeUnit.SECONDS)).isDefined();
        } catch (Exception e) {
            isFirstSignInOnDevice = true;
        }

        setAndStoreEmail(email);
        this.password = password;
        ignoreSelfUpdates = true;
        zMessagingApi.login(CredentialsFactory.emailCredentials(email, password),
                            new LoginListener() {
                                @Override
                                public void onSuccess(Self self) {
                                    bindSelf(self);
                                    setState(AppEntryState.EMAIL_SIGNED_IN);
                                    ignoreSelfUpdates = false;
                                    appEntryStateCallback.tagAppEntryEvent(new LoggedInEvent(false));
                                }

                                @Override
                                public void onFailed(int errorCode, String message, String label) {
                                    if (AppEntryError.EMAIL_INVALID_REQUEST.correspondsTo(errorCode, label)) {
                                        errorCallback.onError(AppEntryError.EMAIL_INVALID_REQUEST);
                                    } else if (AppEntryError.SERVER_CONNECTIVITY_ERROR.correspondsTo(errorCode, "")) {
                                        errorCallback.onError(AppEntryError.SERVER_CONNECTIVITY_ERROR);
                                    } else if (AppEntryError.EMAIL_INVALID_LOGIN_CREDENTIALS.correspondsTo(errorCode, "")) {
                                        errorCallback.onError(AppEntryError.EMAIL_INVALID_LOGIN_CREDENTIALS);
                                    } else if (AppEntryError.TOO_MANY_ATTEMPTS.correspondsTo(errorCode, "")) {
                                        errorCallback.onError(AppEntryError.TOO_MANY_ATTEMPTS);
                                    } else if (AppEntryError.NO_INTERNET.correspondsTo(errorCode, label)) {
                                        errorCallback.onError(AppEntryError.NO_INTERNET);
                                    } else {
                                        errorCallback.onError(AppEntryError.LOGIN_GENERIC_ERROR);
                                    }
                                    ignoreSelfUpdates = false;
                                }
                            });
    }

    @Override
    public void addPhoneToEmail(String countryCode, String phone, final ErrorCallback errorCallback) {
        setAndStoreCountryCode(countryCode);
        setAndStorePhone(phone);
        self.setPhone(countryCode + phone,
                      new CredentialsUpdateListener() {
                          @Override
                          public void onUpdated() {
                              appEntryStateCallback.tagAppEntryEvent(EditSelfUser.phoneAddedSignIn());
                              setState(AppEntryState.EMAIL_SET_CODE);
                          }

                          @Override
                          public void onUpdateFailed(int errorCode, String message, String label) {
                              if (AppEntryError.PHONE_EXISTS.correspondsTo(errorCode, label)) {
                                  errorCallback.onError(AppEntryError.PHONE_EXISTS);
                              } else {
                                  errorCallback.onError(AppEntryError.PHONE_REGISTER_GENERIC_ERROR);
                              }
                          }
                      });
    }

    @Override
    public void resendEmail() {
        switch (entryPoint) {
            case EMAIL_REGISTER:
                appEntryStateCallback.tagAppEntryEvent(new ResentEmailVerificationEvent(OutcomeAttribute.SUCCESS, "", getEmailRegistrationContext()));
                break;
            case PHONE_SIGN_IN:
                appEntryStateCallback.tagAppEntryEvent(EmailVerification.resent(EmailVerification.Context.POST_LOGIN));
                break;
        }
        zMessagingApi.getSelf().resendVerificationEmail(email);
    }

    private void setAndStorePhone(String phone) {
        context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE).edit().putString(PREF_ITEM_PHONE, phone).apply();
        this.phone = phone;
    }

    private void setAndStoreCountryCode(String countryCode) {
        context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE).edit().putString(PREF_ITEM_COUNTRY_CODE, countryCode).apply();
        this.countryCode = countryCode;
    }

    private void setAndStorePhoneVerificationCode(String phoneVerificationCode) {
        context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE).edit().putString(PREF_ITEM_PHONE_VERIFICATION_CODE, countryCode).apply();
        this.phoneVerificationCode = phoneVerificationCode;
    }

    private void setAndStoreName(String name) {
        context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE).edit().putString(PREF_ITEM_NAME, name).apply();
        this.name = name;
    }

    private void setAndStoreEmail(String email) {
        context.getSharedPreferences(PREF_REGISTRATION, Context.MODE_PRIVATE).edit().putString(PREF_ITEM_EMAIL, email).apply();
        this.email = email;
    }

    ////////////////////////
    //
    // Getters from UI
    //
    ////////////////////////

    @Override
    public String getPhone() {
        return phone;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUserId() {
        return self != null && self.getUser() != null ? self.getUser().getId() : null;
    }

    @Override
    public String getInvitationEmail() {
        return invitationEmail;
    }

    @Override
    public String getInvitationPhone() {
        return invitationPhone;
    }

    @Override
    public String getInvitationName() {
        return invitationName;
    }

    @Override
    public Invitations.PersonalToken getInvitationToken() {
        return invitationToken;
    }

    @Override
    public void setRegistrationContext(RegistrationEventContext registrationEventContext) {
        this.registrationEventContext = registrationEventContext;

        switch (registrationEventContext) {
            case PHONE:
            case GENERIC_INVITE_PHONE:
            case PERSONAL_INVITE_PHONE:
                appEntryStateCallback.tagAppEntryEvent(new OpenedPhoneSignUpEvent(registrationEventContext));
                break;
            case EMAIL:
            case GENERIC_INVITE_EMAIL:
            case PERSONAL_INVITE_EMAIL:
                appEntryStateCallback.tagAppEntryEvent(new OpenedEmailSignUpEvent(registrationEventContext));
                break;
        }
    }

    @Override
    public RegistrationEventContext getPhoneRegistrationContext() {
        return registrationEventContext == null ? RegistrationEventContext.PHONE
                                                : registrationEventContext;
    }

    @Override
    public RegistrationEventContext getEmailRegistrationContext() {
        return registrationEventContext == null ? RegistrationEventContext.EMAIL
                                                : registrationEventContext;
    }

    @Override
    public String getCountryCode() {
        return countryCode;
    }

    @Override
    public void onError(ErrorsList.ErrorDescription errorDescription) {
        Timber.i("onError id=%s, conv=%s, resp=%s, time=%s, type=%s",
                 errorDescription.getId(), errorDescription.getConversation(),
                 errorDescription.getResponse(), errorDescription.getTime(), errorDescription.getType());
    }
}
