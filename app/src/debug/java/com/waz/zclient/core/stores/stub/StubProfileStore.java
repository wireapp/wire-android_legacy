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
package com.waz.zclient.core.stores.stub;

import com.waz.api.CredentialsUpdateListener;
import com.waz.api.ImageAsset;
import com.waz.api.Self;
import com.waz.api.User;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.core.stores.profile.IProfileStore;
import com.waz.zclient.core.stores.profile.ProfileStoreObserver;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;

public class StubProfileStore implements IProfileStore {
  @Override
  public User getSelfUser() {
    return null;
  }

  @Override
  public void resendPhoneVerificationCode(String myPhoneNumber, ZMessagingApi.PhoneConfirmationCodeRequestListener confirmationListener) {
    ;
  }

  @Override
  public void addProfileStoreObserver(ProfileStoreObserver profileStoreObserver) {
    ;
  }

  @Override
  public boolean hasProfileImage() {
    return false;
  }

  @Override
  public void setIsFirstLaunch(boolean isFirstLaunch) {
    ;
  }

  @Override
  public void setMyName(String myName) {
    ;
  }

  @Override
  public void addProfileStoreAndUpdateObserver(ProfileStoreObserver profileStoreObserver) {
    ;
  }

  @Override
  public void submitCode(String myPhoneNumber, String code, ZMessagingApi.PhoneNumberVerificationListener verificationListener) {
    ;
  }

  @Override
  public void setAccentColor(Object sender, int color) {
    ;
  }

  @Override
  public String getMyPhoneNumber() {
    return null;
  }

  @Override
  public void setMyEmailAndPassword(String email, String password, CredentialsUpdateListener credentialsUpdateListener) {
    ;
  }

  @Override
  public String getMyName() {
    return null;
  }

  @Override
  public boolean hasIncomingDevices() {
    return false;
  }

  @Override
  public String getMyEmail() {
    return null;
  }

  @Override
  public boolean isEmailVerified() {
    return false;
  }

  @Override
  public void setUserPicture(ImageAsset imageAsset) {
    ;
  }

  @Override
  public void resendVerificationEmail(String myEmail) {
    ;
  }

  @Override
  public int getAccentColor() {
    return 0;
  }

  @Override
  public void setMyEmail(String email, CredentialsUpdateListener credentialsUpdateListener) {
    ;
  }

  @Override
  public void addEmailAndPassword(String email, String password, CredentialsUpdateListener credentialUpdateListener) {
    ;
  }

  @Override
  public void setMyPhoneNumber(String phone, CredentialsUpdateListener credentialsUpdateListener) {
    ;
  }

  @Override
  public void setUser(Self selfUser) {
    ;
  }

  @Override
  public void tearDown() {
    ;
  }

  @Override
  public boolean isPhoneVerified() {
    return false;
  }

  @Override
  public boolean isFirstLaunch() {
    return false;
  }

  @Override
  public void removeProfileStoreObserver(ProfileStoreObserver profileStoreObserver) {
    ;
  }

    @Override
    public boolean isInPrivateMode() {
        return false;
    }

    @Override
    public void setPrivateMode(boolean privateMode) {

    }
}
