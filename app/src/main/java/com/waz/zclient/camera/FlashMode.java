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

class FlashModeConstants {
    static final int FLASH_OFF = 0;
    static final int FLASH_ON = 1;
    static final int FLASH_TORCH = 2;
    static final int FLASH_AUTO = 3;
    static final int FLASH_RED_EYE = 4;
}

public enum FlashMode {

    OFF(FlashModeConstants.FLASH_OFF),
    ON(FlashModeConstants.FLASH_ON),
    TORCH(FlashModeConstants.FLASH_TORCH),
    AUTO(FlashModeConstants.FLASH_AUTO),
    RED_EYE(FlashModeConstants.FLASH_RED_EYE);

    public int mode;

    FlashMode(int mode) {
        this.mode = mode;
    }

    public static FlashMode get(int mode) {
        for (FlashMode state : FlashMode.values()) {
            if (mode == state.mode) {
                return state;
            }
        }
        return OFF;
    }
}
