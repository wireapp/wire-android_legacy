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
package com.waz.zclient.controllers.stubs;

import android.view.View;
import com.waz.api.User;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerScreenObserver;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerSearchObserver;
import java.lang.Override;
import java.lang.String;
import java.util.List;

public class StubPickUserController implements IPickUserController {
  @Override
  public String getSearchFilter() {
    return null;
  }

  @Override
  public void removePickUserSearchControllerObserver(PickUserControllerSearchObserver observer) {
    ;
  }

  @Override
  public boolean isHideWithoutAnimations() {
    return false;
  }

  @Override
  public void hidePickUserWithoutAnimations(IPickUserController.Destination destination) {
    ;
  }

  @Override
  public void removePickUserScreenControllerObserver(PickUserControllerScreenObserver observer) {
    ;
  }

  @Override
  public void removeUser(User user) {
    ;
  }

  @Override
  public void showPickUser(IPickUserController.Destination destination, View anchorView) {
    ;
  }

  @Override
  public List<User> getSelectedUsers() {
    return null;
  }

  @Override
  public boolean isShowingUserProfile() {
    return false;
  }

  @Override
  public void hideUserProfile() {
    ;
  }

  @Override
  public boolean hidePickUser(IPickUserController.Destination destination, boolean closeWithoutSelectingPeople) {
    return false;
  }

  @Override
  public boolean hasSelectedUsers() {
    return false;
  }

  @Override
  public void addPickUserScreenControllerObserver(PickUserControllerScreenObserver observer) {
    ;
  }

  @Override
  public void notifyKeyboardDoneAction() {
    ;
  }

  @Override
  public boolean isShowingPickUser(IPickUserController.Destination destination) {
    return false;
  }

  @Override
  public void showUserProfile(User user, View anchorView) {
    ;
  }

  @Override
  public void addPickUserSearchControllerObserver(PickUserControllerSearchObserver observer) {
    ;
  }

  @Override
  public void setSearchFilter(String newSearchFilter) {
    ;
  }

  @Override
  public void tearDown() {
    ;
  }

  @Override
  public void notifySearchBoxHasNewSearchFilter(String filter) {
    ;
  }

  @Override
  public void addUser(User user) {
    ;
  }

  @Override
  public void resetShowingPickUser(IPickUserController.Destination destination) {
    ;
  }

  @Override
  public boolean searchInputIsInvalidEmail() {
    return false;
  }
}
