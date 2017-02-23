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
package com.waz.zclient.mock;

import android.app.Activity;
import android.view.View;

import com.waz.zclient.controllers.IControllerFactory;
import com.waz.zclient.controllers.accentcolor.IAccentColorController;
import com.waz.zclient.controllers.background.IBackgroundController;
import com.waz.zclient.controllers.background.IDialogBackgroundImageController;
import com.waz.zclient.controllers.calling.ICallingController;
import com.waz.zclient.controllers.camera.ICameraController;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.conversationlist.IConversationListController;
import com.waz.zclient.controllers.currentfocus.IFocusController;
import com.waz.zclient.controllers.deviceuser.IDeviceUserController;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.giphy.IGiphyController;
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController;
import com.waz.zclient.controllers.loadtimelogger.ILoadTimeLoggerController;
import com.waz.zclient.controllers.location.ILocationController;
import com.waz.zclient.controllers.mentioning.IMentioningController;
import com.waz.zclient.controllers.navigation.INavigationController;
import com.waz.zclient.controllers.onboarding.IOnboardingController;
import com.waz.zclient.controllers.orientation.IOrientationController;
import com.waz.zclient.controllers.password.IPasswordController;
import com.waz.zclient.controllers.permission.IRequestPermissionsController;
import com.waz.zclient.controllers.sharing.ISharingController;
import com.waz.zclient.controllers.singleimage.ISingleImageController;
import com.waz.zclient.controllers.spotify.ISpotifyController;
import com.waz.zclient.controllers.streammediaplayer.IStreamMediaPlayerController;
import com.waz.zclient.controllers.stubs.StubAccentColorController;
import com.waz.zclient.controllers.stubs.StubBackgroundController;
import com.waz.zclient.controllers.stubs.StubCallingController;
import com.waz.zclient.controllers.stubs.StubCameraController;
import com.waz.zclient.controllers.stubs.StubConfirmationController;
import com.waz.zclient.controllers.stubs.StubConversationListController;
import com.waz.zclient.controllers.stubs.StubConversationScreenController;
import com.waz.zclient.controllers.stubs.StubDeviceUserController;
import com.waz.zclient.controllers.stubs.StubDialogBackgroundImageController;
import com.waz.zclient.controllers.stubs.StubDrawingController;
import com.waz.zclient.controllers.stubs.StubFocusController;
import com.waz.zclient.controllers.stubs.StubGiphyController;
import com.waz.zclient.controllers.stubs.StubGlobalLayoutController;
import com.waz.zclient.controllers.stubs.StubLoadTimeLoggerController;
import com.waz.zclient.controllers.stubs.StubLocationController;
import com.waz.zclient.controllers.stubs.StubMentioningController;
import com.waz.zclient.controllers.stubs.StubNavigationController;
import com.waz.zclient.controllers.stubs.StubOnboardingController;
import com.waz.zclient.controllers.stubs.StubOrientationController;
import com.waz.zclient.controllers.stubs.StubPasswordController;
import com.waz.zclient.controllers.stubs.StubPickUserController;
import com.waz.zclient.controllers.stubs.StubRequestPermissionsController;
import com.waz.zclient.controllers.stubs.StubSharingController;
import com.waz.zclient.controllers.stubs.StubSingleImageController;
import com.waz.zclient.controllers.stubs.StubSlidingPaneController;
import com.waz.zclient.controllers.stubs.StubSpotifyController;
import com.waz.zclient.controllers.stubs.StubStreamMediaPlayerController;
import com.waz.zclient.controllers.stubs.StubThemeController;
import com.waz.zclient.controllers.stubs.StubUserPreferencesController;
import com.waz.zclient.controllers.stubs.StubUsernamesController;
import com.waz.zclient.controllers.stubs.StubVerificationController;
import com.waz.zclient.controllers.stubs.StubVibratorController;
import com.waz.zclient.controllers.theme.IThemeController;
import com.waz.zclient.controllers.usernames.IUsernamesController;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.controllers.verification.IVerificationController;
import com.waz.zclient.controllers.vibrator.IVibratorController;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;

import static org.mockito.Mockito.spy;

public class MockControllerFactory implements IControllerFactory {
  protected IAccentColorController accentColorController = spy(StubAccentColorController.class);

  protected IBackgroundController backgroundController = spy(StubBackgroundController.class);

  protected IDialogBackgroundImageController dialogBackgroundImageController = spy(StubDialogBackgroundImageController.class);

  protected ICallingController callingController = spy(StubCallingController.class);

  protected ICameraController cameraController = spy(StubCameraController.class);

  protected IConfirmationController confirmationController = spy(StubConfirmationController.class);

  protected IConversationListController conversationListController = spy(StubConversationListController.class);

  protected IFocusController focusController = spy(StubFocusController.class);

  protected IDeviceUserController deviceUserController = spy(StubDeviceUserController.class);

  protected IDrawingController drawingController = spy(StubDrawingController.class);

  protected IGiphyController giphyController = spy(StubGiphyController.class);

  protected IGlobalLayoutController globalLayoutController = spy(StubGlobalLayoutController.class);

  protected ILoadTimeLoggerController loadTimeLoggerController = spy(StubLoadTimeLoggerController.class);

  protected ILocationController locationController = spy(StubLocationController.class);

  protected IMentioningController mentioningController = spy(StubMentioningController.class);

  protected INavigationController navigationController = spy(StubNavigationController.class);

  protected IOnboardingController onboardingController = spy(StubOnboardingController.class);

  protected IOrientationController orientationController = spy(StubOrientationController.class);

  protected IPasswordController passwordController = spy(StubPasswordController.class);

  protected IRequestPermissionsController requestPermissionsController = spy(StubRequestPermissionsController.class);

  protected ISharingController sharingController = spy(StubSharingController.class);

  protected ISingleImageController singleImageController = spy(StubSingleImageController.class);

  protected ISpotifyController spotifyController = spy(StubSpotifyController.class);

  protected IStreamMediaPlayerController streamMediaPlayerController = spy(StubStreamMediaPlayerController.class);

  protected IThemeController themeController = spy(StubThemeController.class);

  protected IUserPreferencesController userPreferencesController = spy(StubUserPreferencesController.class);

  protected IVerificationController verificationController = spy(StubVerificationController.class);

  protected IVibratorController vibratorController = spy(StubVibratorController.class);

  protected IConversationScreenController conversationScreenController = spy(StubConversationScreenController.class);

  protected ISlidingPaneController slidingPaneController = spy(StubSlidingPaneController.class);

  protected IPickUserController pickUserController = spy(StubPickUserController.class);

  protected IUsernamesController usernamesController = spy(StubUsernamesController.class);

  @Override
  public IFocusController getFocusController() {
    return focusController;
  }

  @Override
  public IDrawingController getDrawingController() {
    return drawingController;
  }

  @Override
  public IGlobalLayoutController getGlobalLayoutController() {
    return globalLayoutController;
  }

  @Override
  public boolean isTornDown() {
    return false;
  }

  @Override
  public void setActivity(Activity activity) {
  }

  @Override
  public IVerificationController getVerificationController() {
    return verificationController;
  }

  @Override
  public IStreamMediaPlayerController getStreamMediaPlayerController() {
    return streamMediaPlayerController;
  }

  @Override
  public IOnboardingController getOnboardingController() {
    return onboardingController;
  }

  @Override
  public IVibratorController getVibratorController() {
    return vibratorController;
  }

  @Override
  public IUserPreferencesController getUserPreferencesController() {
    return userPreferencesController;
  }

  @Override
  public IConfirmationController getConfirmationController() {
    return confirmationController;
  }

  @Override
  public IRequestPermissionsController getRequestPermissionsController() {
    return requestPermissionsController;
  }

  @Override
  public IAccentColorController getAccentColorController() {
    return accentColorController;
  }

  @Override
  public ISpotifyController getSpotifyController() {
    return spotifyController;
  }

  @Override
  public void tearDown() {
  }

  @Override
  public IConversationScreenController getConversationScreenController() {
    return conversationScreenController;
  }

  @Override
  public ISlidingPaneController getSlidingPaneController() {
    return slidingPaneController;
  }

  @Override
  public IBackgroundController getBackgroundController() {
    return backgroundController;
  }

  @Override
  public ILocationController getLocationController() {
    return locationController;
  }

  @Override
  public INavigationController getNavigationController() {
    return navigationController;
  }

  @Override
  public IDialogBackgroundImageController getDialogBackgroundImageController() {
    return dialogBackgroundImageController;
  }

  @Override
  public IDeviceUserController getDeviceUserController() {
    return deviceUserController;
  }

  @Override
  public IThemeController getThemeController() {
    return themeController;
  }

  @Override
  public IConversationListController getConversationListController() {
    return conversationListController;
  }

  @Override
  public ISharingController getSharingController() {
    return sharingController;
  }

  @Override
  public ILoadTimeLoggerController getLoadTimeLoggerController() {
    return loadTimeLoggerController;
  }

  @Override
  public IPasswordController getPasswordController() {
    return passwordController;
  }

  @Override
  public void setGlobalLayout(View globalLayoutView) {
  }

  @Override
  public ISingleImageController getSingleImageController() {
    return singleImageController;
  }

  @Override
  public IPickUserController getPickUserController() {
    return pickUserController;
  }

  @Override
  public IGiphyController getGiphyController() {
    return giphyController;
  }

  @Override
  public ICameraController getCameraController() {
    return cameraController;
  }

  @Override
  public IMentioningController getMentioningController() {
    return mentioningController;
  }

  @Override
  public IOrientationController getOrientationController() {
    return orientationController;
  }

  @Override
  public ICallingController getCallingController() {
    return callingController;
  }

    @Override
    public IUsernamesController getUsernameController() {
        return usernamesController;
    }

}
