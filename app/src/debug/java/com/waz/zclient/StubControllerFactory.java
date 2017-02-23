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
package com.waz.zclient;

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
import com.waz.zclient.controllers.stubs.StubThemeController;
import com.waz.zclient.controllers.theme.IThemeController;
import com.waz.zclient.controllers.usernames.IUsernamesController;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.controllers.verification.IVerificationController;
import com.waz.zclient.controllers.vibrator.IVibratorController;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;

/**
 * These classes are NOT auto generated because of the one or two controllers or stores they need to return.
 */
public class StubControllerFactory implements IControllerFactory {

    @Override
    public void setActivity(Activity activity) {

    }

    @Override
    public void setGlobalLayout(View globalLayoutView) {

    }

    @Override
    public IPasswordController getPasswordController() {
        return null;
    }

    @Override
    public void tearDown() {

    }

    @Override
    public boolean isTornDown() {
        return false;
    }

    @Override
    public IAccentColorController getAccentColorController() {
        return null;
    }

    @Override
    public IBackgroundController getBackgroundController() {
        return null;
    }

    @Override
    public IDialogBackgroundImageController getDialogBackgroundImageController() {
        return null;
    }

    @Override
    public ICallingController getCallingController() {
        return null;
    }

    @Override
    public ICameraController getCameraController() {
        return null;
    }

    @Override
    public IConfirmationController getConfirmationController() {
        return null;
    }

    @Override
    public IConversationListController getConversationListController() {
        return null;
    }

    @Override
    public IFocusController getFocusController() {
        return null;
    }

    @Override
    public IDeviceUserController getDeviceUserController() {
        return null;
    }

    @Override
    public IDrawingController getDrawingController() {
        return null;
    }

    @Override
    public IGiphyController getGiphyController() {
        return null;
    }

    @Override
    public IGlobalLayoutController getGlobalLayoutController() {
        return null;
    }

    @Override
    public ILoadTimeLoggerController getLoadTimeLoggerController() {
        return null;
    }

    @Override
    public IMentioningController getMentioningController() {
        return null;
    }

    @Override
    public INavigationController getNavigationController() {
        return null;
    }

    @Override
    public IOnboardingController getOnboardingController() {
        return null;
    }

    @Override
    public IOrientationController getOrientationController() {
        return null;
    }

    @Override
    public ISharingController getSharingController() {
        return null;
    }

    @Override
    public ISingleImageController getSingleImageController() {
        return null;
    }

    @Override
    public ISpotifyController getSpotifyController() {
        return null;
    }

    @Override
    public IStreamMediaPlayerController getStreamMediaPlayerController() {
        return null;
    }

    /**
     * We need a stub implementation of the getThemeController so that test sub classes of the BaseActivity
     * don't crash any tests.
     */
    @Override
    public IThemeController getThemeController() {
        return new StubThemeController();
    }

    @Override
    public IRequestPermissionsController getRequestPermissionsController() {
        return null;
    }

    @Override
    public IUserPreferencesController getUserPreferencesController() {
        return null;
    }

    @Override
    public IVerificationController getVerificationController() {
        return null;
    }

    @Override
    public ILocationController getLocationController() {
        return null;
    }

    @Override
    public IVibratorController getVibratorController() {
        return null;
    }

    @Override
    public IConversationScreenController getConversationScreenController() {
        return null;
    }

    @Override
    public ISlidingPaneController getSlidingPaneController() {
        return null;
    }

    @Override
    public IPickUserController getPickUserController() {
        return null;
    }

    @Override
    public IUsernamesController getUsernameController() {
        return null;
    }

}


