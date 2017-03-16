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

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import com.waz.api.InitListener;
import com.waz.api.Self;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.BackendPicker;
import com.waz.zclient.utils.Callback;
import com.waz.zclient.utils.IntentUtils;

public class LaunchActivity extends BaseActivity implements InitListener {
    public static final String TAG = LaunchActivity.class.getName();
    public static final String APP_PAGE = "APP_PAGE";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getControllerFactory().getLoadTimeLoggerController().appStart();
    }

    @Override
    public int getBaseTheme() {
        return R.style.Theme_Dark;
    }

    @Override
    public void onBaseActivityStart() {
        persistInviteToken();

        new BackendPicker(getApplicationContext()).withBackend(this, new Callback<Void>() {
            @Override
            public void callback(Void aVoid) {
                LaunchActivity.super.onBaseActivityStart();
                getStoreFactory().getZMessagingApiStore().getApi().onInit(LaunchActivity.this);
                injectJava(GlobalTrackingController.class).appLaunched(getIntent());
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        persistInviteToken();
    }

    private void persistInviteToken() {
        String token = IntentUtils.getInviteToken(getIntent());
        if (!TextUtils.isEmpty(token)) {
            getControllerFactory().getUserPreferencesController().setGenericInvitationToken(token);
        }
    }

    // Callbacks //////////////////////////////////////////////////

    @Override
    public void onInitialized(Self self) {
        if (IntentUtils.isEmailVerificationIntent(getIntent())) {
            getStoreFactory().getAppEntryStore().clearCurrentState();
        }

        if (getStoreFactory().getAppEntryStore().getEntryPoint() == null && self.isLoggedIn()) {
            switch (self.getClientRegistrationState()) {
                case PASSWORD_MISSING:
                    startOTRSignIn();
                    return;
            }

            startMain();
        } else {
            startSignUp();
        }
        getControllerFactory().getLoadTimeLoggerController().hideLaunchScreen();
    }

    private void startOTRSignIn() {
        startActivity(new Intent(this, OTRSignInActivity.class));
        finish();
    }

    // Navigation //////////////////////////////////////////////////

    private void startMain() {
        Intent intent = new Intent(this, MainActivity.class);
        String page = IntentUtils.getAppPage(getIntent());
        if (!TextUtils.isEmpty(page)) {
            intent.putExtra(APP_PAGE, page);
        }
        startActivity(intent);
        finish();
    }

    private void startSignUp() {
        startActivity(new Intent(this, AppEntryActivity.class));
        finish();
    }
}
