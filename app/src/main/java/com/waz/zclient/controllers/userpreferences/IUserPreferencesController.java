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
package com.waz.zclient.controllers.userpreferences;

import android.support.annotation.IntDef;
import com.waz.zclient.camera.CameraFacing;

import java.util.List;
import java.util.Set;

public interface IUserPreferencesController {

    @IntDef(SEND_LOCATION_MESSAGE)
    @interface Action { }
    int SEND_LOCATION_MESSAGE = 0;
    int LIKED_MESSAGE = 1;

    void tearDown();

    void reset();

    void setLastAccentColor(final int accentColor);

    int getLastAccentColor();

    boolean showContactsDialog();

    void setRecentCameraDirection(CameraFacing cameraFacing);

    CameraFacing getRecentCameraDirection();

    void setReferralToken(String token);

    String getReferralToken();

    void setGenericInvitationToken(String token);

    String getGenericInvitationToken();

    void setPersonalInvitationToken(String token);

    String getPersonalInvitationToken();

    boolean showStatusBar();

    String getLastCallSessionId();

    void setPostSessionIdToConversation(boolean postSessionIdToConversation);

    boolean isPostSessionIdToConversation();

    String getDeviceId();

    void incrementSpotifyLoginTriesCount();

    int getSpotifyLoginTriesCount();

    boolean isCursorSendButtonEnabled();

    void setVerificationCode(String code);

    void removeVerificationCode();

    String getVerificationCode();

    boolean hasVerificationCode();

    void setCrashException(String exception, String details);

    String getCrashException();

    String getCrashDetails();

    String getSavedFlashState();

    void setSavedFlashState(String state);

    boolean isImageDownloadPolicyWifiOnly();

    boolean hasUserLoggedIn(String userId);

    void userLoggedIn(String userId);

    void setPerformedAction(@Action int action);

    boolean hasPerformedAction(@Action int action);

    /**
     * We return a group number between 1 and 6. Always the same.
     */
    int getABTestingGroup();

    void addRecentEmoji(String emoji);

    List<String> getRecentEmojis();

    void addUnsupportedEmoji(String emoji);

    Set<String> getUnsupportedEmojis();

    boolean hasCheckedForUnsupportedEmojis(int version);

    void setCheckedForUnsupportedEmojis(int version);

}
