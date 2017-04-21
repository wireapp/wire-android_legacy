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
package com.waz.zclient.controllers.spotify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.waz.api.KindOfSpotifyAccount;
import com.waz.api.Spotify;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.ZApplication;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.stores.IStoreFactory;
import com.waz.zclient.core.stores.api.IZMessagingApiStore;
import timber.log.Timber;

import java.util.HashSet;
import java.util.Set;

public class SpotifyController implements ISpotifyController {

    private static final String REDIRECT_URI = "wire://spotify";
    private static final int SPOTIFY_REQUEST_CODE = 59766;

    private IUserPreferencesController userPreferenceController;
    private Set<SpotifyObserver> observers = new HashSet<>();
    private Spotify spotify;
    private Context context;

    public SpotifyController(Context context,
                             IUserPreferencesController userPreferencesController) {
        this.context = context;
        this.userPreferenceController = userPreferencesController;
        init();
    }

    @Override
    public void addSpotifyObserver(SpotifyObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeSpotifyObserver(SpotifyObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void login(Activity activity) {
        if (spotify == null || spotify.getClientId() == null) {
            Timber.w("Something went wrong, unable to login to Spotify");
        }
        userPreferenceController.incrementSpotifyLoginTriesCount();
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(spotify.getClientId(),
                                                                                  AuthenticationResponse.Type.CODE,
                                                                                  REDIRECT_URI);
        builder.setScopes(new String[] {"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(activity, SPOTIFY_REQUEST_CODE, request);
    }

    @Override
    public void logout() {
        spotify.disconnect();
        reset();
        notifyLogout();
    }

    @Override
    public boolean isLoggedIn() {
        return spotify != null && spotify.isConnected();
    }

    @Override
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != SPOTIFY_REQUEST_CODE) {
            return;
        }
        AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);
        if (response.getType() == AuthenticationResponse.Type.CODE) {
            spotify.connect(response.getCode(), new Spotify.ConnectCallback() {
                @Override
                public void onConnect(KindOfSpotifyAccount kindOfAccount) {
                    if (kindOfAccount == KindOfSpotifyAccount.PREMIUM) {
                        notifyLoginSuccess();
                    } else {
                        notifyLoginFailed();
                    }
                }
            });
        } else {
            notifyLoginFailed();
        }
    }

    private void init() {
        if (!isLoggedIn()) {
            return;
        }
        notifyLoginSuccess();
    }

    private void reset() {
        spotify.disconnect();
    }

    private void notifyLoginFailed() {
        logout();
        for (SpotifyObserver observer : observers) {
            observer.onLoginFailed();
        }
    }

    private void notifyLoginSuccess() {
        for (SpotifyObserver observer : observers) {
            observer.onLoginSuccess();
        }
    }

    private void notifyLogout() {
        for (SpotifyObserver observer : observers) {
            observer.onLogout();
        }
    }

    @Override
    public void tearDown() {
        observers.clear();
        userPreferenceController = null;
        spotify = null;
        context = null;
    }

    @Override
    public Config getPlayerConfig() {
        return new Config(context, spotify.getAccessToken(), spotify.getClientId());
    }

    @Override
    public boolean shouldShowLoginHint() {
        if (isLoggedIn()) {
            return false;
        }
        return userPreferenceController.getSpotifyLoginTriesCount() < 1;
    }

    @Override
    public void setActivity(Activity activity) {
        final IStoreFactory storeFactory = ZApplication.from(activity).getStoreFactory();
        if (storeFactory == null || storeFactory.isTornDown()) {
            return;
        }
        final IZMessagingApiStore zMessagingApiStore = storeFactory.getZMessagingApiStore();
        final ZMessagingApi api = zMessagingApiStore.getApi();
        this.spotify = api.getSpotify();
    }
}
