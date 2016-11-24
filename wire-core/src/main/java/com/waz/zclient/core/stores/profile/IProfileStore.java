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
package com.waz.zclient.core.stores.profile;

import com.waz.api.CredentialsUpdateListener;
import com.waz.api.ImageAsset;
import com.waz.api.Self;
import com.waz.api.User;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.core.stores.IStore;

public interface IProfileStore extends IStore {

    /* add an observer to this store */
    void addProfileStoreObserver(ProfileStoreObserver profileStoreObserver);

    void addProfileStoreAndUpdateObserver(ProfileStoreObserver profileStoreObserver);

    /* remove an observer from this store */
    void removeProfileStoreObserver(ProfileStoreObserver profileStoreObserver);

    void setUser(Self selfUser);

    /*  The name of the user */
    String getMyName();

    void setMyName(String myName);

    /*  The email of the user */
    String getMyEmail();

    /*  The phone numer of the user */
    String getMyPhoneNumber();

    boolean isEmailVerified();

    boolean isPhoneVerified();

    boolean hasIncomingDevices();

    void setMyPhoneNumber(String phone, CredentialsUpdateListener credentialsUpdateListener);

    void setMyEmail(String email, CredentialsUpdateListener credentialsUpdateListener);

    void setMyEmailAndPassword(String email, String password, CredentialsUpdateListener credentialsUpdateListener);

    void resendVerificationEmail(String myEmail);

    void resendPhoneVerificationCode(String myPhoneNumber,
                                     ZMessagingApi.PhoneConfirmationCodeRequestListener confirmationListener);

    User getSelfUser();

    /* the color chosen by the user */
    int getAccentColor();

    /*  if the user chose a new color */
    void setAccentColor(Object sender, int color);

    /*  indicates if it app is launched for the first time */
    boolean isFirstLaunch();

    /* notifies self store that app is launched for the very first time */
    void setIsFirstLaunch(boolean isFirstLaunch);

    void setUserPicture(ImageAsset imageAsset);

    boolean hasProfileImage();

    void addEmailAndPassword(String email, String password, CredentialsUpdateListener credentialUpdateListener);

    void submitCode(String myPhoneNumber,
                    String code,
                    ZMessagingApi.PhoneNumberVerificationListener verificationListener);

    boolean isInPrivateMode();

    void setPrivateMode(boolean privateMode);
}
