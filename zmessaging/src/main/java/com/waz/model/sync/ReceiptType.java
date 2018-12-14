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
package com.waz.model.sync;

import java.util.HashMap;
import java.util.Map;

public enum ReceiptType {

    Delivery("delivery"),
    EphemeralExpired("ephemeral-expired"),
    Read("read");

    public final String name;

    ReceiptType(String name) {
        this.name = name;
    }

    private static Map<String, ReceiptType> byName = new HashMap<>();
    static {
        for (ReceiptType value : ReceiptType.values()) {
            byName.put(value.name, value);
        }
    }

    public static ReceiptType fromName(String name) {
        ReceiptType result = byName.get(name);
        return (result == null) ? Delivery : result;
    }
}
