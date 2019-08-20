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
package com.waz.zclient.controllers;

import android.app.Activity;
import android.view.View;
import com.waz.zclient.controllers.camera.ICameraController;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.deviceuser.IDeviceUserController;
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController;
import com.waz.zclient.controllers.location.ILocationController;
import com.waz.zclient.controllers.navigation.INavigationController;
import com.waz.zclient.controllers.singleimage.ISingleImageController;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.controllers.verification.IVerificationController;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;

public interface IControllerFactory {
  IGlobalLayoutController getGlobalLayoutController();

  IConfirmationController getConfirmationController();

  INavigationController getNavigationController();

  IPickUserController getPickUserController();

  boolean isTornDown();

  ISingleImageController getSingleImageController();

  IVerificationController getVerificationController();

  IConversationScreenController getConversationScreenController();

  ILocationController getLocationController();

  IUserPreferencesController getUserPreferencesController();

  void setGlobalLayout(View globalLayoutView);

  ISlidingPaneController getSlidingPaneController();

  void setActivity(Activity activity);

  IDeviceUserController getDeviceUserController();

  void tearDown();

  ICameraController getCameraController();
}
