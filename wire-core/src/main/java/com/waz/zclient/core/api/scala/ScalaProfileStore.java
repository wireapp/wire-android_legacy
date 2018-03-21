/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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

import com.waz.api.ZMessagingApi;
import com.waz.zclient.core.stores.profile.ProfileStore;

public class ScalaProfileStore extends ProfileStore {
    public static final String TAG = ScalaProfileStore.class.getName();


    public ScalaProfileStore(ZMessagingApi zMessagingApi) {
        setUser(zMessagingApi.getSelf());
    }

    @Override
    public void tearDown() {
        if (selfUser != null) {
            selfUser = null;
        }
    }

    @Override
    public void resendVerificationEmail(String myEmail) {
        if (selfUser != null) {
            selfUser.resendVerificationEmail(myEmail);
        }
    }
}
