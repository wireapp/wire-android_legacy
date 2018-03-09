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
package com.waz.zclient.pages.main.pickuser.controller;

import android.content.Context;
import com.waz.model.UserId;

import java.util.HashSet;
import java.util.Set;

public class PickUserController implements IPickUserController {

    private Set<PickUserControllerScreenObserver> pickUserControllerScreenObservers;
    private Set<Destination> visibleDestinations;

    private boolean isShowingUserProfile;
    private boolean hideWithoutAnimations;

    private Context context;

    public PickUserController(Context context) {
        this.context = context;

        pickUserControllerScreenObservers = new HashSet<>();
        visibleDestinations = new HashSet<>();

        isShowingUserProfile = false;
        hideWithoutAnimations = false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserControllerScreenObserver - Screen actions
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addPickUserScreenControllerObserver(PickUserControllerScreenObserver observer) {
        pickUserControllerScreenObservers.add(observer);
    }

    @Override
    public void removePickUserScreenControllerObserver(PickUserControllerScreenObserver observer) {
        pickUserControllerScreenObservers.remove(observer);
    }

    // Showing people picker
    @Override
    public void showPickUser(Destination destination) {
        if (isShowingPickUser(destination)) {
            return;
        }
        visibleDestinations.add(destination);
        for (PickUserControllerScreenObserver pickUserControllerScreenObserver : pickUserControllerScreenObservers) {
            pickUserControllerScreenObserver.onShowPickUser(destination);
        }
    }

    @Override
    public boolean hidePickUser(Destination destination) {
        if (!isShowingPickUser(destination)) {
            return false;
        }
        for (PickUserControllerScreenObserver pickUserControllerScreenObserver : pickUserControllerScreenObservers) {
            pickUserControllerScreenObserver.onHidePickUser(destination);
        }
        visibleDestinations.remove(destination);
        return true;
    }

    @Override
    public boolean isHideWithoutAnimations() {
        return hideWithoutAnimations;
    }

    @Override
    public void hidePickUserWithoutAnimations(Destination destination) {
        hideWithoutAnimations = true;
        hidePickUser(destination);
        hideWithoutAnimations = false;
    }

    @Override
    public boolean isShowingPickUser(Destination destination) {
        return visibleDestinations.contains(destination);
    }

    @Override
    public void showUserProfile(UserId userId) {
        for (PickUserControllerScreenObserver pickUserControllerScreenObserver : pickUserControllerScreenObservers) {
            pickUserControllerScreenObserver.onShowUserProfile(userId);
        }
        isShowingUserProfile = true;
    }

    @Override
    public void hideUserProfile() {
        for (PickUserControllerScreenObserver pickUserControllerScreenObserver : pickUserControllerScreenObservers) {
            pickUserControllerScreenObserver.onHideUserProfile();
        }
        isShowingUserProfile = false;
    }

    @Override
    public boolean isShowingUserProfile() {
        // The PickUser fragment is only showing user profile for phone,
        // for tablet the user profile is shown in a dialog and this should always return false
        return isShowingUserProfile;
    }

    @Override
    public void tearDown() {
        context = null;
        if (pickUserControllerScreenObservers != null) {
            pickUserControllerScreenObservers.clear();
            pickUserControllerScreenObservers = null;
        }
    }
}
