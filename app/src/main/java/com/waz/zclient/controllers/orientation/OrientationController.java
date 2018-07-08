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
package com.waz.zclient.controllers.orientation;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;
import com.waz.zclient.utils.SquareOrientation;

import java.util.HashSet;
import java.util.Set;

public class OrientationController implements IOrientationController {
    public static final String TAG = OrientationController.class.getName();

    private final OrientationEventListener orientationEventListener;
    Set<OrientationControllerObserver> orientationControllerObservers = new HashSet<>();

    public OrientationController(final Context context) {
        orientationEventListener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                notifyOrientationHasChanged(SquareOrientation.getOrientation(orientation, context));
            }
        };
        orientationEventListener.enable();
    }

    @Override
    public void tearDown() {
        orientationEventListener.disable();
    }

    @Override
    public void addOrientationControllerObserver(OrientationControllerObserver orientationControllerObserver) {
        orientationControllerObservers.add(orientationControllerObserver);
    }

    @Override
    public void removeOrientationControllerObserver(OrientationControllerObserver orientationControllerObserver) {
        orientationControllerObservers.remove(orientationControllerObserver);
    }

    private void notifyOrientationHasChanged(SquareOrientation squareOrientation) {
        for (OrientationControllerObserver orientationControllerObserver : orientationControllerObservers) {
            orientationControllerObserver.onOrientationHasChanged(squareOrientation);
        }
    }
}
