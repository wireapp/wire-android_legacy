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
package com.waz.zclient.camera;

import android.hardware.Camera;

public enum CameraFacing {

    BACK(Camera.CameraInfo.CAMERA_FACING_BACK),
    FRONT(Camera.CameraInfo.CAMERA_FACING_FRONT);

    public int facing;

    CameraFacing(int facing) {
        this.facing = facing;
    }

    public static CameraFacing getFacing(int f) {
        for (CameraFacing facing : CameraFacing.values()) {
            if (facing.facing == f) {
                return facing;
            }
        }
        throw new IllegalArgumentException(
            "Not a proper Camera Direction facing value. Use only the Enum");
    }
}
