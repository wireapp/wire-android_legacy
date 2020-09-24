/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.camera;

import android.hardware.camera2.CaptureRequest;

public enum FlashMode {

    OFF(CaptureRequest.FLASH_MODE_OFF),
    AUTO(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH),
    ON(CaptureRequest.FLASH_MODE_SINGLE),
    TORCH(CaptureRequest.FLASH_MODE_TORCH);

    public int mode;

    FlashMode(int mode) {
        this.mode = mode;
    }

    public static FlashMode get(int mode) {
        for (FlashMode state: FlashMode.values()) {
            if (mode == state.mode) {
                return state;
            }
        }
        return OFF;
    }
}
