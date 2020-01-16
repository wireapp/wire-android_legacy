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
package com.waz.zclient.controllers.camera;

import com.waz.service.assets.Content;
import com.waz.zclient.pages.main.profile.camera.CameraContext;

import java.util.HashSet;
import java.util.Set;

public class CameraController implements ICameraController {

    private Set<CameraActionObserver> cameraActionObservers = new HashSet<>();

    private CameraContext cameraContext;

    @Override
    public void addCameraActionObserver(CameraActionObserver cameraActionObserver) {
        cameraActionObservers.add(cameraActionObserver);
    }

    @Override
    public void removeCameraActionObserver(CameraActionObserver cameraActionObserver) {
        cameraActionObservers.remove(cameraActionObserver);
    }

    @Override
    public void openCamera(CameraContext cameraContext) {
        this.cameraContext = cameraContext;
        for (CameraActionObserver cameraActionObserver : cameraActionObservers) {
            cameraActionObserver.onOpenCamera(cameraContext);
        }
    }

    @Override
    public void closeCamera(CameraContext cameraContext) {
        for (CameraActionObserver cameraActionObserver : cameraActionObservers) {
            cameraActionObserver.onCloseCamera(this.cameraContext);
        }
    }

    @Override
    public void onBitmapSelected(Content content, CameraContext cameraContext) {
        for (CameraActionObserver cameraActionObserver : cameraActionObservers) {
            cameraActionObserver.onBitmapSelected(content, cameraContext);
        }
    }

    @Override
    public void onCameraNotAvailable(CameraContext cameraContext) {
        for (CameraActionObserver cameraActionObserver : cameraActionObservers) {
            cameraActionObserver.onCameraNotAvailable();
        }
    }

    @Override
    public void tearDown() {
        cameraActionObservers.clear();
        cameraContext = null;
    }
}
