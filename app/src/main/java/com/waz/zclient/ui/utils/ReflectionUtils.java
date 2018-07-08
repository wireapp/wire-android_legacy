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
package com.waz.zclient.ui.utils;

import java.lang.reflect.Field;


/**
 * Collection of tools to access the private fields of a class
 *
 * @author lahoda
 */
public class ReflectionUtils {

    private ReflectionUtils() {}

    /**
     * Get a single named private declared field
     *
     * @param type
     * @param name
     * @return requested field or null if not found
     */
    public static Field getInheritedPrivateField(Class<?> type, String name) {
        Class<?> i = type;
        while (i != null && i != Object.class) {
            for (Field field : i.getDeclaredFields()) {
                if (!field.isSynthetic() && field.getName().equals(name)) {
                    return field;
                }
            }
            i = i.getSuperclass();
        }
        return null;
    }


}
