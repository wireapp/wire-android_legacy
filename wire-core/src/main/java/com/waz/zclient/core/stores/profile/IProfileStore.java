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

import com.waz.api.Self;
import com.waz.api.User;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.core.stores.IStore;

public interface IProfileStore extends IStore {

    /* add an observer to this store */
    void addProfileStoreObserver(ProfileStoreObserver profileStoreObserver);

    /* remove an observer from this store */
    void removeProfileStoreObserver(ProfileStoreObserver profileStoreObserver);

    void setUser(Self selfUser);

    /*  The email of the user */
    String getMyEmail();

    void resendVerificationEmail(String myEmail);

    void resendPhoneVerificationCode(String myPhoneNumber,
                                     ZMessagingApi.PhoneConfirmationCodeRequestListener confirmationListener);

    User getSelfUser();

    /* the color chosen by the user */
    int getAccentColor();

    void submitCode(String myPhoneNumber,
                    String code,
                    ZMessagingApi.PhoneNumberVerificationListener verificationListener);
}
