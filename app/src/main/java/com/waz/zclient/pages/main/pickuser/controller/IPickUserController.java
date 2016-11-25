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

import android.support.annotation.IntDef;
import android.view.View;
import com.waz.api.User;

import java.util.List;

public interface IPickUserController {

    enum Destination {
        CONVERSATION_LIST,
        PARTICIPANTS,
        CURSOR
    }

    @IntDef({CONVERSATION_LIST,
             CONVERSATION,
             STARTUI
    })
    @interface ContactListDestination { }
    int CONVERSATION_LIST = 0;
    int CONVERSATION = 1;
    int STARTUI = 2;

    void addPickUserScreenControllerObserver(PickUserControllerScreenObserver observer);

    void removePickUserScreenControllerObserver(PickUserControllerScreenObserver observer);

    // Showing people picker
    void showPickUser(Destination destination, View anchorView);

    /**
     * @return true, if a picker was hidden, false otherwise
     */
    boolean hidePickUser(Destination destination, boolean closeWithoutSelectingPeople);

    boolean isHideWithoutAnimations();

    void hidePickUserWithoutAnimations(Destination destination);

    boolean isShowingPickUser(Destination destination);

    void resetShowingPickUser(Destination destination);

    void showUserProfile(User user, View anchorView);

    void hideUserProfile();

    boolean isShowingUserProfile();

    void addPickUserSearchControllerObserver(PickUserControllerSearchObserver observer);

    void removePickUserSearchControllerObserver(PickUserControllerSearchObserver observer);

    void notifySearchBoxHasNewSearchFilter(String filter);

    void notifyKeyboardDoneAction();

    void addUser(User user);

    void removeUser(User user);

    String getSearchFilter();

    List<User> getSelectedUsers();

    boolean hasSelectedUsers();

    boolean searchInputIsInvalidEmail();

    void setSearchFilter(String newSearchFilter);

    void tearDown();
}
