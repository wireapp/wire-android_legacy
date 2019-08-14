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
package com.waz.api;

import java.util.HashMap;

public enum OtrClientType {
    PHONE("phone"), TABLET("tablet"), DESKTOP("desktop");

    public final String deviceClass;

    private static HashMap<String, OtrClientType> classMap = new HashMap<>();
    static {
        classMap.put(PHONE.deviceClass, PHONE);
        classMap.put(TABLET.deviceClass, TABLET);
        classMap.put(DESKTOP.deviceClass, DESKTOP);
    }

    public static OtrClientType fromDeviceClass(String cls) {
        OtrClientType res = classMap.get(cls);
        return (res == null) ? PHONE : res;
    }

    OtrClientType(String cls) {
        this.deviceClass = cls;
    }
}
