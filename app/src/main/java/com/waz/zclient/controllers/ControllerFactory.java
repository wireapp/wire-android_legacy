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
import android.content.Context;
import android.view.View;
import com.waz.zclient.controllers.camera.CameraController;
import com.waz.zclient.controllers.camera.ICameraController;
import com.waz.zclient.controllers.confirmation.ConfirmationController;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.deviceuser.DeviceUserController;
import com.waz.zclient.controllers.deviceuser.IDeviceUserController;
import com.waz.zclient.controllers.globallayout.GlobalLayoutController;
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController;
import com.waz.zclient.controllers.location.ILocationController;
import com.waz.zclient.controllers.location.LocationController;
import com.waz.zclient.controllers.navigation.INavigationController;
import com.waz.zclient.controllers.navigation.NavigationController;
import com.waz.zclient.controllers.singleimage.ISingleImageController;
import com.waz.zclient.controllers.singleimage.SingleImageController;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.controllers.userpreferences.UserPreferencesController;
import com.waz.zclient.controllers.verification.IVerificationController;
import com.waz.zclient.controllers.verification.VerificationController;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenController;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController;
import com.waz.zclient.pages.main.conversationpager.controller.SlidingPaneController;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserController;

public class ControllerFactory implements IControllerFactory {

  protected ICameraController cameraController;

  protected IConfirmationController confirmationController;

  protected IDeviceUserController deviceUserController;

  protected IGlobalLayoutController globalLayoutController;

  protected ILocationController locationController;

  protected INavigationController navigationController;

  protected ISingleImageController singleImageController;

  protected IUserPreferencesController userPreferencesController;

  protected IVerificationController verificationController;

  protected IConversationScreenController conversationScreenController;

  protected ISlidingPaneController slidingPaneController;

  protected IPickUserController pickUserController;

  protected boolean isTornDown;

  protected Context context;

  public ControllerFactory(Context context) {
    this.context = context;
    this.isTornDown = false;
  }

  @Override
  public ISlidingPaneController getSlidingPaneController() {
    verifyLifecycle();
    if (slidingPaneController == null) {
      slidingPaneController = new SlidingPaneController();
    }
    return slidingPaneController;
  }

  @Override
  public void tearDown() {
    this.isTornDown = true;
    if (cameraController != null) {
      cameraController.tearDown();
      cameraController = null;
    }
    if (confirmationController != null) {
      confirmationController.tearDown();
      confirmationController = null;
    }
    if (deviceUserController != null) {
      deviceUserController.tearDown();
      deviceUserController = null;
    }
    if (globalLayoutController != null) {
      globalLayoutController.tearDown();
      globalLayoutController = null;
    }
    if (locationController != null) {
      locationController.tearDown();
      locationController = null;
    }
    if (navigationController != null) {
      navigationController.tearDown();
      navigationController = null;
    }
    if (singleImageController != null) {
      singleImageController.tearDown();
      singleImageController = null;
    }
    if (userPreferencesController != null) {
      userPreferencesController.tearDown();
      userPreferencesController = null;
    }
    if (verificationController != null) {
      verificationController.tearDown();
      verificationController = null;
    }
    if (conversationScreenController != null) {
      conversationScreenController.tearDown();
      conversationScreenController = null;
    }
    if (slidingPaneController != null) {
      slidingPaneController.tearDown();
      slidingPaneController = null;
    }
    if (pickUserController != null) {
      pickUserController.tearDown();
      pickUserController = null;
    }
    this.context = null;
  }

  @Override
  public void setGlobalLayout(View globalLayoutView) {
    getGlobalLayoutController().setGlobalLayout(globalLayoutView);
  }

  @Override
  public INavigationController getNavigationController() {
    verifyLifecycle();
    if (navigationController == null) {
      navigationController = new NavigationController(this.context);
    }
    return navigationController;
  }

  @Override
  public boolean isTornDown() {
    return isTornDown;
  }

  @Override
  public IVerificationController getVerificationController() {
    verifyLifecycle();
    if (verificationController == null) {
      verificationController = new VerificationController(getUserPreferencesController());
    }
    return verificationController;
  }

  @Override
  public IUserPreferencesController getUserPreferencesController() {
    verifyLifecycle();
    if (userPreferencesController == null) {
      userPreferencesController = new UserPreferencesController(this.context);
    }
    return userPreferencesController;
  }

  @Override
  public IPickUserController getPickUserController() {
    verifyLifecycle();
    if (pickUserController == null) {
      pickUserController = new PickUserController();
    }
    return pickUserController;
  }

  @Override
  public IConfirmationController getConfirmationController() {
    verifyLifecycle();
    if (confirmationController == null) {
      confirmationController = new ConfirmationController();
    }
    return confirmationController;
  }

  @Override
  public IGlobalLayoutController getGlobalLayoutController() {
    verifyLifecycle();
    if (globalLayoutController == null) {
      globalLayoutController = new GlobalLayoutController();
    }
    return globalLayoutController;
  }

  @Override
  public ILocationController getLocationController() {
    verifyLifecycle();
    if (locationController == null) {
      locationController = new LocationController();
    }
    return locationController;
  }

  @Override
  public ICameraController getCameraController() {
    verifyLifecycle();
    if (cameraController == null) {
      cameraController = new CameraController();
    }
    return cameraController;
  }

  @Override
  public IConversationScreenController getConversationScreenController() {
    verifyLifecycle();
    if (conversationScreenController == null) {
      conversationScreenController = new ConversationScreenController();
    }
    return conversationScreenController;
  }

  protected final void verifyLifecycle() {
    if (isTornDown) {
      throw new IllegalStateException("ControllerFactory is already torn down");
    }
  }

  @Override
  public void setActivity(Activity activity) {
    getGlobalLayoutController().setActivity(activity);
  }

  @Override
  public IDeviceUserController getDeviceUserController() {
    verifyLifecycle();
    if (deviceUserController == null) {
      deviceUserController = new DeviceUserController(this.context);
    }
    return deviceUserController;
  }

  @Override
  public ISingleImageController getSingleImageController() {
    verifyLifecycle();
    if (singleImageController == null) {
      singleImageController = new SingleImageController();
    }
    return singleImageController;
  }

}
