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
package com.waz.zclient.controllers;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import com.waz.zclient.controllers.accentcolor.AccentColorController;
import com.waz.zclient.controllers.accentcolor.IAccentColorController;
import com.waz.zclient.controllers.background.BackgroundController;
import com.waz.zclient.controllers.background.DialogBackgroundImageController;
import com.waz.zclient.controllers.background.IBackgroundController;
import com.waz.zclient.controllers.background.IDialogBackgroundImageController;
import com.waz.zclient.controllers.calling.CallingController;
import com.waz.zclient.controllers.calling.ICallingController;
import com.waz.zclient.controllers.camera.CameraController;
import com.waz.zclient.controllers.camera.ICameraController;
import com.waz.zclient.controllers.confirmation.ConfirmationController;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.conversationlist.ConversationListController;
import com.waz.zclient.controllers.conversationlist.IConversationListController;
import com.waz.zclient.controllers.currentfocus.FocusController;
import com.waz.zclient.controllers.currentfocus.IFocusController;
import com.waz.zclient.controllers.deviceuser.DeviceUserController;
import com.waz.zclient.controllers.deviceuser.IDeviceUserController;
import com.waz.zclient.controllers.drawing.DrawingController;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.giphy.GiphyController;
import com.waz.zclient.controllers.giphy.IGiphyController;
import com.waz.zclient.controllers.globallayout.GlobalLayoutController;
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController;
import com.waz.zclient.controllers.loadtimelogger.ILoadTimeLoggerController;
import com.waz.zclient.controllers.location.ILocationController;
import com.waz.zclient.controllers.location.LocationController;
import com.waz.zclient.controllers.mentioning.IMentioningController;
import com.waz.zclient.controllers.mentioning.MentioningController;
import com.waz.zclient.controllers.navigation.INavigationController;
import com.waz.zclient.controllers.navigation.NavigationController;
import com.waz.zclient.controllers.onboarding.IOnboardingController;
import com.waz.zclient.controllers.onboarding.OnboardingController;
import com.waz.zclient.controllers.orientation.IOrientationController;
import com.waz.zclient.controllers.orientation.OrientationController;
import com.waz.zclient.controllers.password.IPasswordController;
import com.waz.zclient.controllers.password.PasswordController;
import com.waz.zclient.controllers.permission.IRequestPermissionsController;
import com.waz.zclient.controllers.permission.RequestPermissionsController;
import com.waz.zclient.controllers.sharing.ISharingController;
import com.waz.zclient.controllers.sharing.SharingController;
import com.waz.zclient.controllers.singleimage.ISingleImageController;
import com.waz.zclient.controllers.singleimage.SingleImageController;
import com.waz.zclient.controllers.spotify.ISpotifyController;
import com.waz.zclient.controllers.spotify.SpotifyController;
import com.waz.zclient.controllers.streammediaplayer.IStreamMediaPlayerController;
import com.waz.zclient.controllers.streammediaplayer.StreamMediaPlayerController;
import com.waz.zclient.controllers.theme.IThemeController;
import com.waz.zclient.controllers.theme.ThemeController;
import com.waz.zclient.controllers.usernames.IUsernamesController;
import com.waz.zclient.controllers.usernames.UsernamesController;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.controllers.userpreferences.UserPreferencesController;
import com.waz.zclient.controllers.verification.IVerificationController;
import com.waz.zclient.controllers.verification.VerificationController;
import com.waz.zclient.controllers.vibrator.IVibratorController;
import com.waz.zclient.controllers.vibrator.VibratorController;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenController;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController;
import com.waz.zclient.pages.main.conversationpager.controller.SlidingPaneController;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserController;

public abstract class Base$$ControllerFactory implements IControllerFactory {
  protected IAccentColorController accentColorController;

  protected IBackgroundController backgroundController;

  protected IDialogBackgroundImageController dialogBackgroundImageController;

  protected ICallingController callingController;

  protected ICameraController cameraController;

  protected IConfirmationController confirmationController;

  protected IConversationListController conversationListController;

  protected IFocusController focusController;

  protected IDeviceUserController deviceUserController;

  protected IDrawingController drawingController;

  protected IGiphyController giphyController;

  protected IGlobalLayoutController globalLayoutController;

  protected ILoadTimeLoggerController loadTimeLoggerController;

  protected ILocationController locationController;

  protected IMentioningController mentioningController;

  protected INavigationController navigationController;

  protected IOnboardingController onboardingController;

  protected IOrientationController orientationController;

  protected IPasswordController passwordController;

  protected IRequestPermissionsController requestPermissionsController;

  protected ISharingController sharingController;

  protected ISingleImageController singleImageController;

  protected ISpotifyController spotifyController;

  protected IStreamMediaPlayerController streamMediaPlayerController;

  protected IThemeController themeController;

  protected IUserPreferencesController userPreferencesController;

  protected IVerificationController verificationController;

  protected IVibratorController vibratorController;

  protected IConversationScreenController conversationScreenController;

  protected ISlidingPaneController slidingPaneController;

  protected IPickUserController pickUserController;

  protected IUsernamesController usernamesController;

  protected boolean isTornDown;

  protected Context context;

  public Base$$ControllerFactory(Context context) {
    this.context = context;
    this.isTornDown = false;
  }

  @Override
  public ISharingController getSharingController() {
    verifyLifecycle();
    if (sharingController == null) {
      sharingController = new SharingController();
    }
    return sharingController;
  }

  @Override
  public IPasswordController getPasswordController() {
    verifyLifecycle();
    if (passwordController == null) {
      passwordController = new PasswordController();
    }
    return passwordController;
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
    if (accentColorController != null) {
      accentColorController.tearDown();
      accentColorController = null;
    }
    if (backgroundController != null) {
      backgroundController.tearDown();
      backgroundController = null;
    }
    if (dialogBackgroundImageController != null) {
      dialogBackgroundImageController.tearDown();
      dialogBackgroundImageController = null;
    }
    if (callingController != null) {
      callingController.tearDown();
      callingController = null;
    }
    if (cameraController != null) {
      cameraController.tearDown();
      cameraController = null;
    }
    if (confirmationController != null) {
      confirmationController.tearDown();
      confirmationController = null;
    }
    if (conversationListController != null) {
      conversationListController.tearDown();
      conversationListController = null;
    }
    if (focusController != null) {
      focusController.tearDown();
      focusController = null;
    }
    if (deviceUserController != null) {
      deviceUserController.tearDown();
      deviceUserController = null;
    }
    if (drawingController != null) {
      drawingController.tearDown();
      drawingController = null;
    }
    if (giphyController != null) {
      giphyController.tearDown();
      giphyController = null;
    }
    if (globalLayoutController != null) {
      globalLayoutController.tearDown();
      globalLayoutController = null;
    }
    if (loadTimeLoggerController != null) {
      loadTimeLoggerController.tearDown();
      loadTimeLoggerController = null;
    }
    if (locationController != null) {
      locationController.tearDown();
      locationController = null;
    }
    if (mentioningController != null) {
      mentioningController.tearDown();
      mentioningController = null;
    }
    if (navigationController != null) {
      navigationController.tearDown();
      navigationController = null;
    }
    if (onboardingController != null) {
      onboardingController.tearDown();
      onboardingController = null;
    }
    if (orientationController != null) {
      orientationController.tearDown();
      orientationController = null;
    }
    if (passwordController != null) {
      passwordController.tearDown();
      passwordController = null;
    }
    if (requestPermissionsController != null) {
      requestPermissionsController.tearDown();
      requestPermissionsController = null;
    }
    if (sharingController != null) {
      sharingController.tearDown();
      sharingController = null;
    }
    if (singleImageController != null) {
      singleImageController.tearDown();
      singleImageController = null;
    }
    if (spotifyController != null) {
      spotifyController.tearDown();
      spotifyController = null;
    }
    if (streamMediaPlayerController != null) {
      streamMediaPlayerController.tearDown();
      streamMediaPlayerController = null;
    }
    if (themeController != null) {
      themeController.tearDown();
      themeController = null;
    }
    if (userPreferencesController != null) {
      userPreferencesController.tearDown();
      userPreferencesController = null;
    }
    if (verificationController != null) {
      verificationController.tearDown();
      verificationController = null;
    }
    if (vibratorController != null) {
      vibratorController.tearDown();
      vibratorController = null;
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
    if (usernamesController != null) {
      usernamesController.tearDown();
      usernamesController = null;
    }
    this.context = null;
  }

  @Override
  public IStreamMediaPlayerController getStreamMediaPlayerController() {
    verifyLifecycle();
    if (streamMediaPlayerController == null) {
      streamMediaPlayerController = new StreamMediaPlayerController(this.context, getSpotifyController());
    }
    return streamMediaPlayerController;
  }

  @Override
  public void setGlobalLayout(View globalLayoutView) {
    getGlobalLayoutController().setGlobalLayout(globalLayoutView);
  }

  @Override
  public IFocusController getFocusController() {
    verifyLifecycle();
    if (focusController == null) {
      focusController = new FocusController();
    }
    return focusController;
  }

  @Override
  public IOrientationController getOrientationController() {
    verifyLifecycle();
    if (orientationController == null) {
      orientationController = new OrientationController(this.context);
    }
    return orientationController;
  }

  @Override
  public IMentioningController getMentioningController() {
    verifyLifecycle();
    if (mentioningController == null) {
      mentioningController = new MentioningController();
    }
    return mentioningController;
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
  public IConversationListController getConversationListController() {
    verifyLifecycle();
    if (conversationListController == null) {
      conversationListController = new ConversationListController();
    }
    return conversationListController;
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
  public IThemeController getThemeController() {
    verifyLifecycle();
    if (themeController == null) {
      themeController = new ThemeController(this.context);
    }
    return themeController;
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
      pickUserController = new PickUserController(this.context);
    }
    return pickUserController;
  }

  @Override
  public ICallingController getCallingController() {
    verifyLifecycle();
    if (callingController == null) {
      callingController = new CallingController();
    }
    return callingController;
  }

  @Override
  public IRequestPermissionsController getRequestPermissionsController() {
    verifyLifecycle();
    if (requestPermissionsController == null) {
      requestPermissionsController = new RequestPermissionsController();
    }
    return requestPermissionsController;
  }

  @Override
  public IVibratorController getVibratorController() {
    verifyLifecycle();
    if (vibratorController == null) {
      vibratorController = new VibratorController(this.context);
    }
    return vibratorController;
  }

  @Override
  public ISpotifyController getSpotifyController() {
    verifyLifecycle();
    if (spotifyController == null) {
      spotifyController = new SpotifyController(this.context, getUserPreferencesController());
    }
    return spotifyController;
  }

  @Override
  public IGiphyController getGiphyController() {
    verifyLifecycle();
    if (giphyController == null) {
      giphyController = new GiphyController();
    }
    return giphyController;
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
  public IDialogBackgroundImageController getDialogBackgroundImageController() {
    verifyLifecycle();
    if (dialogBackgroundImageController == null) {
      dialogBackgroundImageController = new DialogBackgroundImageController();
    }
    return dialogBackgroundImageController;
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
    getOrientationController().setActivity(activity);
    getSpotifyController().setActivity(activity);
    getUsernameController().setActivity(activity);
  }

  @Override
  public IOnboardingController getOnboardingController() {
    verifyLifecycle();
    if (onboardingController == null) {
      onboardingController = new OnboardingController(this.context);
    }
    return onboardingController;
  }

  @Override
  public IBackgroundController getBackgroundController() {
    verifyLifecycle();
    if (backgroundController == null) {
      backgroundController = new BackgroundController();
    }
    return backgroundController;
  }

  protected abstract void initLoadTimeLoggerController();

  @Override
  public IDeviceUserController getDeviceUserController() {
    verifyLifecycle();
    if (deviceUserController == null) {
      deviceUserController = new DeviceUserController(this.context);
    }
    return deviceUserController;
  }

  @Override
  public IDrawingController getDrawingController() {
    verifyLifecycle();
    if (drawingController == null) {
      drawingController = new DrawingController();
    }
    return drawingController;
  }

  @Override
  public ILoadTimeLoggerController getLoadTimeLoggerController() {
    verifyLifecycle();
    initLoadTimeLoggerController();
    return loadTimeLoggerController;
  }

  @Override
  public ISingleImageController getSingleImageController() {
    verifyLifecycle();
    if (singleImageController == null) {
      singleImageController = new SingleImageController();
    }
    return singleImageController;
  }

  @Override
  public IAccentColorController getAccentColorController() {
    verifyLifecycle();
    if (accentColorController == null) {
      accentColorController = new AccentColorController(this.context, getUserPreferencesController());
    }
    return accentColorController;
  }

    @Override
    public IUsernamesController getUsernameController() {
        verifyLifecycle();
        if (usernamesController == null) {
            usernamesController = new UsernamesController();
        }
        return usernamesController;
    }
}
