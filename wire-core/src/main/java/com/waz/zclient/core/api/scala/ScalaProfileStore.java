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

import com.waz.api.AccentColor;
import com.waz.api.CredentialsUpdateListener;
import com.waz.api.ImageAsset;
import com.waz.api.KindOfAccess;
import com.waz.api.KindOfVerification;
import com.waz.api.User;
import com.waz.api.ZMessagingApi;
import com.waz.api.impl.AccentColors;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.core.stores.profile.ProfileStore;

public class ScalaProfileStore extends ProfileStore {
    public static final String TAG = ScalaProfileStore.class.getName();

    private ZMessagingApi zMessagingApi;
    private final AccentColor[] accentColors;
    private String myName;
    private String myEmail;
    private String myPhoneNumber;
    private String myUsername;
    private int myColor;
    private boolean emailIsVerified;
    private boolean phoneIsVerified;

    public ScalaProfileStore(ZMessagingApi zMessagingApi) {
        this.zMessagingApi = zMessagingApi;
        accentColors = AccentColors.getColors();
        setUser(zMessagingApi.getSelf());
    }

    @Override
    public void tearDown() {
        if (selfUser != null) {
            selfUser.removeUpdateListener(this);
            selfUser = null;
        }
        myName = null;
        myEmail = null;
        myPhoneNumber = null;
        zMessagingApi = null;
        myUsername = null;
        emailIsVerified = false;
        phoneIsVerified = false;
    }

    @Override
    public String getMyName() {
        return myName;
    }

    @Override
    public void setMyName(String myName) {
        if (this.myName.equals(myName)) {
            return;
        }
        selfUser.setName(myName);
    }

    @Override
    public String getMyEmail() {
        return selfUser.getEmail();
    }

    @Override
    public String getMyPhoneNumber() {
        return selfUser.getPhone();
    }

    @Override
    public void setMyPhoneNumber(final String myPhone, final CredentialsUpdateListener credentialsUpdateListener) {
        selfUser.setPhone(myPhone, credentialsUpdateListener);
    }

    @Override
    public void setMyEmail(final String email, final CredentialsUpdateListener credentialsUpdateListener) {
        selfUser.setEmail(email, credentialsUpdateListener);
    }

    @Override
    public void setMyEmailAndPassword(final String email, final String password, final CredentialsUpdateListener credentialsUpdateListener) {
        selfUser.setPassword(password, new CredentialsUpdateListener() {
            @Override
            public void onUpdated() {
                selfUser.setEmail(email, credentialsUpdateListener);
            }

            @Override
            public void onUpdateFailed(int errorCode, String message, String label) {
                if (errorCode == AppEntryError.FORBIDDEN.errorCode) {
                    // Ignore error when password is already set
                    selfUser.setEmail(email, credentialsUpdateListener);
                } else {
                    credentialsUpdateListener.onUpdateFailed(errorCode, message, label);
                }
            }
        });
    }

    @Override
    public void resendVerificationEmail(String myEmail) {
        selfUser.resendVerificationEmail(myEmail);
    }

    @Override
    public void resendPhoneVerificationCode(String myPhoneNumber, final ZMessagingApi.PhoneConfirmationCodeRequestListener confirmationListener) {
        zMessagingApi.requestPhoneConfirmationCode(myPhoneNumber,
                                                   KindOfAccess.REGISTRATION,
                                                   confirmationListener);
    }

    @Override
    public User getSelfUser() {
        return selfUser.getUser();
    }

    @Override
    public int getAccentColor() {
        return selfUser.getAccent().getColor();
    }


    @Override
    public void setAccentColor(Object sender, int myColor) {
        this.myColor = myColor;

        // identify color
        if (accentColors.length <= 0) {
            return;
        }

        AccentColor selectedColor = accentColors[0];

        for (AccentColor accentColor : accentColors) {
            if (myColor == accentColor.getColor()) {
                selectedColor = accentColor;
                break;
            }
        }

        selfUser.setAccent(selectedColor);
    }

    @Override
    public void setUserPicture(ImageAsset imageAsset) {
        selfUser.setPicture(imageAsset);
    }

    @Override
    public boolean hasProfileImage() {
        return !selfUser.getPicture().isEmpty();
    }

    @Override
    public boolean isEmailVerified() {
        return selfUser.isEmailVerified();
    }

    @Override
    public boolean isPhoneVerified() {
        return selfUser.isPhoneVerified();
    }

    @Override
    public void addEmailAndPassword(final String email,
                                    final String password,
                                    final CredentialsUpdateListener credentialUpdateListener) {
        selfUser.setPassword(password, new CredentialsUpdateListener() {
            @Override
            public void onUpdated() {
                selfUser.setEmail(email, credentialUpdateListener);
            }

            @Override
            public void onUpdateFailed(int errorCode, String message, String label) {
                // Edge case where password was set on another device while email/pw
                // were being added on this one.
                if (errorCode == AppEntryError.FORBIDDEN.errorCode) {
                    selfUser.setEmail(email, credentialUpdateListener);
                } else {
                    credentialUpdateListener.onUpdateFailed(AppEntryError.PHONE_ADD_PASSWORD.errorCode,
                                                            "",
                                                            AppEntryError.PHONE_ADD_PASSWORD.label);
                }
            }
        });
    }

    @Override
    public void submitCode(String myPhoneNumber,
                           String code,
                           ZMessagingApi.PhoneNumberVerificationListener verificationListener) {
        zMessagingApi.verifyPhoneNumber(myPhoneNumber,
                                        code,
                                        KindOfVerification.VERIFY_ON_UPDATE,
                                        verificationListener);
    }

    /**
     * User has been updated in core.
     */
    @Override
    public void updated() {
        if (selfUser == null) {
            return;
        }

        if (!selfUser.getName().equals(myName)) {
            this.myName = selfUser.getName();
            notifyNameHasChanged(this, myName);
        }

        if (!selfUser.getUsername().equals(myUsername)) {
            this.myUsername = selfUser.getUsername();
            notifyUsernameHasChanged(myUsername);
        }

        if (!selfUser.getEmail().equals(myEmail) ||
            selfUser.isEmailVerified() != emailIsVerified) {
            this.myEmail = selfUser.getEmail();
            this.emailIsVerified = selfUser.isEmailVerified();
            notifyEmailHasChanged(myEmail, this.emailIsVerified);
        }

        if (!selfUser.getPhone().equals(myPhoneNumber) ||
            selfUser.isPhoneVerified() != phoneIsVerified) {
            this.myPhoneNumber = selfUser.getPhone();
            this.phoneIsVerified = selfUser.isPhoneVerified();
            notifyPhoneHasChanged(myPhoneNumber, this.phoneIsVerified);
        }

        if (selfUser.getAccent().getColor() != myColor) {
            myColor = selfUser.getAccent().getColor();
            notifyMyColorHasChanged(this, myColor);
        }
    }
}
