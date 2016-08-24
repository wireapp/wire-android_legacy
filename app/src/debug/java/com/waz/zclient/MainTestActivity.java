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

import com.waz.api.CommonConnections;
import com.waz.api.IConversation;
import com.waz.api.MessagesList;
import com.waz.api.Self;
import com.waz.api.User;
import com.waz.api.VoiceChannel;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.calling.CallingObserver;
import com.waz.zclient.controllers.navigation.NavigationControllerObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.core.stores.api.ZMessagingApiStoreObserver;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.profile.ProfileStoreObserver;
import com.waz.zclient.pages.main.MainPhoneFragment;
import com.waz.zclient.pages.main.MainTabletFragment;
import com.waz.zclient.pages.main.connectivity.ConnectivityFragment;
import com.waz.zclient.pages.main.grid.GridFragment;
import com.waz.zclient.pages.startup.UpdateFragment;

public class MainTestActivity extends TestActivity implements MainPhoneFragment.Container,
                                                              MainTabletFragment.Container,
                                                              GridFragment.Container,
                                                              ConnectivityFragment.Container,
                                                              UpdateFragment.Container,
                                                              ProfileStoreObserver,
                                                              AccentColorObserver,
                                                              ConnectStoreObserver,
                                                              NavigationControllerObserver,
                                                              CallingObserver,
                                                              VoiceChannel.JoinCallback,
                                                              OtrDeviceLimitFragment.Container,
                                                              ZMessagingApiStoreObserver {

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {

    }

    @Override
    public void onStartCall(boolean withVideo) {

    }

    @Override
    public void onConnectUserUpdated(User user, IConnectStore.UserRequester usertype) {

    }

    @Override
    public void onCommonConnectionsUpdated(CommonConnections commonConnections) {

    }

    @Override
    public void onInviteRequestSent(IConversation conversation) {

    }

    @Override
    public void logout() {

    }

    @Override
    public void manageDevices() {

    }

    @Override
    public void dismissOtrDeviceLimitFragment() {

    }

    @Override
    public void onOpenUrl(String url) {

    }

    @Override
    public void onCallJoined() {

    }

    @Override
    public void onAlreadyJoined() {

    }

    @Override
    public void onCallJoinError(String s) {

    }

    @Override
    public void onConversationTooBig(int i, int i1) {

    }

    @Override
    public void onVoiceChannelFull(int i) {

    }

    @Override
    public void onPageVisible(Page page) {

    }

    @Override
    public void onPageStateHasChanged(Page page) {

    }

    @Override
    public void onAccentColorChangedRemotely(Object sender, int color) {

    }

    @Override
    public void onMyNameHasChanged(Object sender, String myName) {

    }

    @Override
    public void onMyEmailHasChanged(String myEmail, boolean isVerified) {

    }

    @Override
    public void onMyPhoneHasChanged(String myPhone, boolean isVerified) {

    }

    @Override
    public void onPhoneUpdateFailed(String myPhone, int errorCode, String message, String label) {

    }

    @Override
    public void onMyEmailAndPasswordHasChanged(String myEmail) {

    }

    @Override
    public void onInitialized(Self self) {

    }

    @Override
    public void onLogout() {

    }

    @Override
    public void onForceClientUpdate() {

    }
}
