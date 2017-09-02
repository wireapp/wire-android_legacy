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
import com.waz.api.UpdateListener;

import java.util.HashSet;
import java.util.Set;

public abstract class ProfileStore implements IProfileStore, UpdateListener {

    // observers
    private Set<ProfileStoreObserver> profileStoreObservers = new HashSet<>();

    protected Self selfUser;

    /* add an observer to this store */
    public void addProfileStoreObserver(ProfileStoreObserver profileStoreObserver) {
        profileStoreObservers.add(profileStoreObserver);
    }

    @Override
    public void setUser(Self selfUser) {
        if (this.selfUser != null) {
            this.selfUser.removeUpdateListener(this);
        }
        this.selfUser = selfUser;

        if (selfUser == null) {
            return;
        }
        this.selfUser.addUpdateListener(this);
        updated();
    }

    /* remove an observer from this store */
    public void removeProfileStoreObserver(ProfileStoreObserver profileStoreObserver) {
        profileStoreObservers.remove(profileStoreObserver);
    }

    protected void notifyMyColorHasChanged(Object sender, int color) {
        for (ProfileStoreObserver profileStoreObserver : profileStoreObservers) {
            profileStoreObserver.onAccentColorChangedRemotely(sender, color);
        }
    }
}
