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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.ShareCompat;
import android.util.AttributeSet;
import android.view.View;
import com.waz.api.Self;
import com.waz.utils.wrappers.AndroidURI;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.controllers.SharingController;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback;
import com.waz.zclient.controllers.sharing.SharedContentType;
import com.waz.zclient.conversation.ShareToMultipleFragment;
import com.waz.zclient.core.stores.api.ZMessagingApiStoreObserver;
import com.waz.zclient.utils.AssetUtils;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.menus.ConfirmationMenu;
import timber.log.Timber;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShareActivity extends BaseActivity implements AccentColorObserver,
                                                           ZMessagingApiStoreObserver {

    private static final String TAG = ShareActivity.class.getName();

    private static final int EXTERNAL_STORAGE_PERMISSION_CODE = 1;
    private static final String[] EXTERNAL_STORAGE_PERMISSIONS = {android.Manifest.permission.READ_EXTERNAL_STORAGE};

    private ConfirmationMenu confirmationMenu;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_share);

        confirmationMenu = ViewUtils.getView(this, R.id.cm__conversation_list__login_prompt);

        if (!PermissionUtils.hasSelfPermissions(this, EXTERNAL_STORAGE_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this,
                EXTERNAL_STORAGE_PERMISSIONS,
                EXTERNAL_STORAGE_PERMISSION_CODE);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .add(R.id.fl_main_content,
                    ShareToMultipleFragment.newInstance(),
                    ShareToMultipleFragment.TAG())
                .commit();
        }

        handleIncomingIntent();
    }

    @Override
    public int getBaseTheme() {
        return R.style.Theme_Dark;
    }

    @Override
    public View onCreateView(String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        return super.onCreateView(name, context, attrs);
    }

    @Override
    public void onStart() {
        getStoreFactory().zMessagingApiStore().addApiObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        super.onStart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIncomingIntent();
    }

    @Override
    public void onStop() {
        getStoreFactory().zMessagingApiStore().removeApiObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        confirmationMenu = null;
        super.onDestroy();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        confirmationMenu.setButtonColor(color);
    }


    private void enterApplication(Self self) {
        if (!self.isLoggedIn()) {
            // User not logged in
            showUserNotLoggedInDialog();
        }
    }

    public void showUserNotLoggedInDialog() {
        confirmationMenu.setHeader(getString(R.string.sharing__user_not_logged_in__dialog__title));
        confirmationMenu.setText(getString(R.string.sharing__user_not_logged_in__dialog__message));
        confirmationMenu.setPositiveButton(getString(R.string.sharing__user_not_logged_in__dialog__confirm));
        confirmationMenu.setNegativeButton("");
        confirmationMenu.setCallback(new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                finish();
            }

            @Override
            public void negativeButtonClicked() {

            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {

            }
        });

        confirmationMenu.animateToShow(true);
    }

    private void handleIncomingIntent() {
        ShareCompat.IntentReader intentReader = ShareCompat.IntentReader.from(this);
        if (!intentReader.isShareIntent()) {
            finish();
            return;
        }
        if (intentReader.getType().equals("text/plain")) {
            getSharingController().publishTextContent(String.valueOf(intentReader.getText()));
        } else {
            final Set<URI> sharedFileUris = new HashSet<>();
            if (intentReader.isMultipleShare()) {
                for (int i = 0; i < intentReader.getStreamCount(); i++) {
                    URI uri = new AndroidURI(intentReader.getStream(i));
                    if (uri != null) {
                        sharedFileUris.add(uri);
                    }
                }
            } else {
                Uri uri = intentReader.getStream();
                if (uri != null) {
                    sharedFileUris.add(new AndroidURI(uri));
                }
            }
            if (sharedFileUris.size() == 0) {
                finish();
                return;
            }
            boolean sharingImages = intentReader.getType().startsWith("image/");
            final SharedContentType contentType;
            if (sharingImages && sharedFileUris.size() == 1) {
                contentType = SharedContentType.IMAGE;
            } else {
                contentType = SharedContentType.FILE;
            }

            List<URI> sanitisedUris = new ArrayList<>();
            for (URI uri : sharedFileUris) {
                String path = AssetUtils.getPath(getApplicationContext(), uri);
                if (path == null) {
                    Timber.e("Something went wrong, unable to retrieve path");
                    sanitisedUris.add(uri);
                } else {
                    sanitisedUris.add(AndroidURIUtil.fromFile(new File(path)));
                }
            }

            switch (contentType) {
                case IMAGE:
                    getSharingController().publishImageContent(sanitisedUris);
                    break;
                case FILE:
                    getSharingController().publishFileContent(sanitisedUris);
                    break;
            }
        }
    }

    @Override
    public void onInitialized(Self self) {
        enterApplication(self);
    }

    @Override
    public void onLogout() {

    }

    @Override
    public void onForceClientUpdate() {

    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(ShareToMultipleFragment.TAG());
        if (fragment != null && ((ShareToMultipleFragment) fragment).onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    private SharingController getSharingController() {
        return injectJava(SharingController.class);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ShareToMultipleFragment fragment = ((ShareToMultipleFragment) getSupportFragmentManager().findFragmentByTag(ShareToMultipleFragment.TAG()));
        if (fragment != null) {
            fragment.updatePreview();
        }
    }
}
