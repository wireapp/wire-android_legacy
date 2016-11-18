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

import com.waz.api.CoreList;
import com.waz.api.OtrClient;
import com.waz.api.Self;
import com.waz.api.UpdateListener;

import java.util.HashSet;
import java.util.Set;

public abstract class ProfileStore implements IProfileStore, UpdateListener {

    // observers
    protected Set<ProfileStoreObserver> profileStoreObservers = new HashSet<ProfileStoreObserver>();

    // is first launch
    private boolean isFirstLaunch;

    protected Self selfUser;
    private CoreList<OtrClient> otherClients;
    private boolean hasOtherUnverifiedClients;

    @Override
    public boolean isFirstLaunch() {
        return isFirstLaunch;
    }

    /* add an observer to this store */
    public void addProfileStoreObserver(ProfileStoreObserver profileStoreObserver) {
        profileStoreObservers.add(profileStoreObserver);
    }

    @Override
    public void addProfileStoreAndUpdateObserver(ProfileStoreObserver profileStoreObserver) {
        profileStoreObservers.add(profileStoreObserver);
        if (selfUser == null) {
            return;
        }
        profileStoreObserver.onMyNameHasChanged(this, selfUser.getName());
        profileStoreObserver.onMyEmailHasChanged(selfUser.getEmail(), selfUser.isEmailVerified());
        profileStoreObserver.onMyPhoneHasChanged(selfUser.getPhone(), selfUser.isPhoneVerified());
        profileStoreObserver.onAccentColorChangedRemotely(this, selfUser.getAccent().getColor());
        profileStoreObserver.onMyUsernameHasChanged(selfUser.getUsername());
    }

    @Override
    public void setUser(Self selfUser) {
        if (this.selfUser != null) {
            this.selfUser.removeUpdateListener(this);
            otherClients = null;
        }
        this.selfUser = selfUser;

        if (selfUser == null) {
            return;
        }

        otherClients = selfUser.getIncomingOtrClients();
        this.selfUser.addUpdateListener(this);
        updated();
    }

    /* remove an observer from this store */
    public void removeProfileStoreObserver(ProfileStoreObserver profileStoreObserver) {
        profileStoreObservers.remove(profileStoreObserver);
    }

    @Override
    public void setIsFirstLaunch(boolean isFirstLaunch) {
        this.isFirstLaunch = isFirstLaunch;
    }

    protected void notifyMyColorHasChanged(Object sender, int color) {
        for (ProfileStoreObserver profileStoreObserver : profileStoreObservers) {
            profileStoreObserver.onAccentColorChangedRemotely(sender, color);
        }
    }

    protected void notifyNameHasChanged(Object sender, String myName) {
        for (ProfileStoreObserver profileStoreObserver : profileStoreObservers) {
            profileStoreObserver.onMyNameHasChanged(sender, myName);
        }
    }

    protected void notifyEmailHasChanged(String myEmail, boolean isVerified) {
        for (ProfileStoreObserver profileStoreObserver : profileStoreObservers) {
            profileStoreObserver.onMyEmailHasChanged(myEmail, isVerified);
        }
    }

    protected void notifyPhoneHasChanged(String myPhone, boolean isVerified) {
        for (ProfileStoreObserver profileStoreObserver : profileStoreObservers) {
            profileStoreObserver.onMyPhoneHasChanged(myPhone, isVerified);
        }
    }

    protected void notifyEmailAndPasswordHasChanged(String email) {
        for (ProfileStoreObserver profileStoreObserver : profileStoreObservers) {
            profileStoreObserver.onMyEmailAndPasswordHasChanged(email);
        }
    }

    protected void notifyUsernameHasChanged(String myUsername) {
        for (ProfileStoreObserver profileStoreObserver : profileStoreObservers) {
            profileStoreObserver.onMyUsernameHasChanged(myUsername);
        }
    }

    public boolean hasIncomingDevices() {
        return otherClients.size() > 0;
    }
}
