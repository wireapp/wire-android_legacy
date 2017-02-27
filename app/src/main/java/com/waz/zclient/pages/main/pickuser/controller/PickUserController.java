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
package com.waz.zclient.pages.main.pickuser.controller;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import com.waz.api.User;
import com.waz.zclient.pages.main.profile.validator.EmailValidator;
import com.waz.zclient.utils.LayoutSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PickUserController implements IPickUserController {

    private Set<PickUserControllerScreenObserver> pickUserControllerScreenObservers;
    private Set<PickUserControllerSearchObserver> pickUserControllerSearchObservers;
    private Set<Destination> visibleDestinations;

    private boolean isShowingUserProfile;
    private boolean hideWithoutAnimations;

    private final EmailValidator emailValidator;
    private String searchFilter;
    private Context context;
    private List<User> selectedUsers;

    public PickUserController(Context context) {
        this.context = context;
        this.emailValidator = EmailValidator.newInstance();

        pickUserControllerScreenObservers = new HashSet<>();
        pickUserControllerSearchObservers = new HashSet<>();
        visibleDestinations = new HashSet<>();
        selectedUsers = new ArrayList<>();

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
    public void showPickUser(Destination destination, View anchorView) {
        if (isShowingPickUser(destination)) {
            return;
        }
        visibleDestinations.add(destination);
        for (PickUserControllerScreenObserver pickUserControllerScreenObserver : pickUserControllerScreenObservers) {
            pickUserControllerScreenObserver.onShowPickUser(destination, anchorView);
        }
    }

    @Override
    public boolean hidePickUser(Destination destination, boolean closeWithoutSelectingPeople) {
        if (!isShowingPickUser(destination)) {
            return false;
        }
        for (PickUserControllerScreenObserver pickUserControllerScreenObserver : pickUserControllerScreenObservers) {
            pickUserControllerScreenObserver.onHidePickUser(destination, closeWithoutSelectingPeople);
        }
        visibleDestinations.remove(destination);
        selectedUsers.clear();
        return true;
    }

    @Override
    public boolean isHideWithoutAnimations() {
        return hideWithoutAnimations;
    }

    @Override
    public void hidePickUserWithoutAnimations(Destination destination) {
        hideWithoutAnimations = true;
        hidePickUser(destination, false);
        hideWithoutAnimations = false;
    }

    @Override
    public boolean isShowingPickUser(Destination destination) {
        return visibleDestinations.contains(destination);
    }

    @Override
    public void resetShowingPickUser(Destination destination) {
        visibleDestinations.remove(destination);
    }

    @Override
    public void showUserProfile(User user, View anchorView) {
        for (PickUserControllerScreenObserver pickUserControllerScreenObserver : pickUserControllerScreenObservers) {
            pickUserControllerScreenObserver.onShowUserProfile(user, anchorView);
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
        return isShowingUserProfile && LayoutSpec.isPhone(context);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserControllerSearchObserver - Search Actions
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addPickUserSearchControllerObserver(PickUserControllerSearchObserver observer) {
        pickUserControllerSearchObservers.add(observer);
    }

    @Override
    public void removePickUserSearchControllerObserver(PickUserControllerSearchObserver observer) {
        pickUserControllerSearchObservers.remove(observer);
    }

    @Override
    public void notifySearchBoxHasNewSearchFilter(String filter) {
        for (PickUserControllerSearchObserver pickUserControllerSearchObserver : pickUserControllerSearchObservers) {
            pickUserControllerSearchObserver.onSearchBoxHasNewSearchFilter(filter);
        }
    }

    @Override
    public void notifyKeyboardDoneAction() {
        // Protect against ConcurrentModificationException - an observer might unsubscribe while notification is in progress
        Set<PickUserControllerSearchObserver> observers = new HashSet<>(pickUserControllerSearchObservers.size());
        observers.addAll(pickUserControllerSearchObservers);

        for (PickUserControllerSearchObserver observer : observers) {
            observer.onKeyboardDoneAction();
        }
    }

    @Override
    public void addUser(User user) {
        if (!selectedUsers.contains(user)) {
            selectedUsers.add(user);
        }
        for (PickUserControllerSearchObserver pickUserControllerSearchObserver : pickUserControllerSearchObservers) {
            pickUserControllerSearchObserver.onSelectedUserAdded(selectedUsers, user);
        }
    }

    @Override
    public void removeUser(User user) {
        selectedUsers.remove(user);
        final boolean isEmpty = selectedUsers.size() == 0;
        for (PickUserControllerSearchObserver pickUserControllerSearchObserver : pickUserControllerSearchObservers) {
            pickUserControllerSearchObserver.onSelectedUserRemoved(selectedUsers, user);
            if (isEmpty) {
                pickUserControllerSearchObserver.onSearchBoxIsEmpty();
            }
        }
    }

    @Override
    public String getSearchFilter() {
        return searchFilter;
    }

    @Override
    public List<User> getSelectedUsers() {
        return new ArrayList<>(selectedUsers);
    }

    @Override
    public boolean hasSelectedUsers() {
        return getSelectedUsers().size() > 0;
    }

    @Override
    public boolean searchInputIsInvalidEmail() {
        if (TextUtils.isEmpty(searchFilter)) {
            return false;
        }
        return !emailValidator.validate(searchFilter);
    }

    @Override
    public void setSearchFilter(String newSearchFilter) {
        if (newSearchFilter.equalsIgnoreCase(searchFilter)) {
            return;
        }
        searchFilter = newSearchFilter;
        if (TextUtils.isEmpty(searchFilter)) {
            for (PickUserControllerSearchObserver pickUserControllerSearchObserver : pickUserControllerSearchObservers) {
                pickUserControllerSearchObserver.onSearchBoxIsEmpty();
            }
        } else {
            notifySearchBoxHasNewSearchFilter(searchFilter);
        }
    }

    @Override
    public void tearDown() {
        context = null;
        if (pickUserControllerScreenObservers != null) {
            pickUserControllerScreenObservers.clear();
            pickUserControllerScreenObservers = null;
        }
        if (pickUserControllerSearchObservers != null) {
            pickUserControllerSearchObservers.clear();
            pickUserControllerSearchObservers = null;
        }
        selectedUsers = null;
    }

    private boolean hasOnlyTextContent() {
        return !hasSelectedUsers() && !TextUtils.isEmpty(searchFilter);
    }
}
