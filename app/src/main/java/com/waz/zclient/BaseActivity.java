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

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;
import android.view.View;
import com.waz.api.Permission;
import com.waz.api.PermissionProvider;
import com.waz.zclient.controllers.IControllerFactory;
import com.waz.zclient.core.stores.IStoreFactory;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BaseActivity extends BaseScalaActivity implements ServiceContainer,
                                                               PermissionProvider {
    private boolean started = false;
    private ScalaPermissionRequest permissionRequest;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(getBaseTheme());
    }

    @StyleRes
    protected int getBaseTheme() {
        return getControllerFactory().getThemeController().getTheme();
    }

    @Override
    public void onBaseActivityStart() {
        ((ZApplication) getApplication()).ensureInitialized();

        super.onBaseActivityStart();

        getControllerFactory().setActivity(this);
        if (!started) {
            started = true;
            getStoreFactory().getZMessagingApiStore().getApi().onResume();
        }
        getStoreFactory().getZMessagingApiStore().getApi().setPermissionProvider(this);

        View contentView = ViewUtils.getView(getWindow().getDecorView(), android.R.id.content);
        if (contentView != null) {
            getControllerFactory().setGlobalLayout(contentView);
        }
    }

    @Override
    public void onStop() {
        if (started) {
            getStoreFactory().getZMessagingApiStore().getApi().onPause();
            started = false;
        }
        getStoreFactory().getZMessagingApiStore().getApi().removePermissionProvider(this);
        super.onStop();
    }

    @Override
    public void finish() {
        if (started) {
            getStoreFactory().getZMessagingApiStore().getApi().onPause();
            started = false;
        }
        super.finish();
    }

    @Override
    public IStoreFactory getStoreFactory() {
        return ZApplication.from(this).getStoreFactory();
    }

    @Override
    public IControllerFactory getControllerFactory() {
        return ZApplication.from(this).getControllerFactory();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionRequest != null && requestCode == ScalaPermissionRequest.REQUEST_SE_PERMISSION) {

            TrackingUtils.tagChangedContactsPermissionEvent(injectJava(GlobalTrackingController.class),
                                                            permissions,
                                                            grantResults);

            if (grantResults.length < 0) {
                permissionRequest.cancel();
            } else if (PermissionUtils.verifyPermissions(grantResults)) {
                permissionRequest.grant();
            } else {
                final PermissionProvider.ResponseHandler responseHandler = permissionRequest.getTargetResponseHandler();
                if (responseHandler == null) {
                    return;
                }
                final Map<Permission, Permission.Status> resultMap = new HashMap<>();
                for (int i = 0; i < grantResults.length; i++) {
                    int result = grantResults[i];
                    Permission permission = Permission.forId(permissions[i]);
                    resultMap.put(permission, result == PackageManager.PERMISSION_GRANTED ? Permission.Status.GRANTED
                                                                                          : Permission.Status.DENIED);
                }
                responseHandler.handleResponse(resultMap);
            }
            permissionRequest = null;
        } else {
            getControllerFactory().getRequestPermissionsController().onRequestPermissionsResult(requestCode, grantResults);
        }
    }

    @Override
    public void requestPermissions(Set<Permission> ps, PermissionProvider.ResponseHandler callback) {
        final ScalaPermissionRequest sePermissionRequest = new ScalaPermissionRequest(this, callback, ps);
        if (PermissionUtils.hasSelfPermissions(this, sePermissionRequest.getPermissionIds())) {
            sePermissionRequest.grant();
        } else {
            // TODO: Maybe use {@link PermissionUtils.shouldShowRequestPermissionRationale(this, sePermissionRequest.getPermissionIds())} to explain why we need those permissions
            permissionRequest = sePermissionRequest;
            sePermissionRequest.proceed();
        }
    }
}
