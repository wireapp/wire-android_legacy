/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service;

public enum PlaybackRoute {
    Invalid(-1), Earpiece(0), Speaker(1), Headset(2), Bluetooth(3);

    public final int avsIndex;

    PlaybackRoute(int avsIndex) {
        this.avsIndex = avsIndex;
    }

    static PlaybackRoute fromAvsIndex(int avsIndex) {
        for (PlaybackRoute value: PlaybackRoute.values()) {
            if (value.avsIndex == avsIndex) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown/invalid avs index: " + avsIndex);
    }
}
