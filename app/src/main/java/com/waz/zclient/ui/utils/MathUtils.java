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

public class MathUtils {
    /**
     * @return the value, if it is inside [min, max]
     *         min if the value is smaller then min
     *         max if the value is bigger then max
     */
    public static long clamp(long value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * @return the value, if it is inside [min, max]
     *         min if the value is smaller then min
     *         max if the value is bigger then max
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static boolean floatEqual(float val1, float val2) {
        return Float.compare(val2, val1) == 0;
    }

    /**
     * Removes the value of a binary flag from an original value if that flag was
     * set, otherwise returns the original value
     *
     * E.g.
     *      removeBinaryFlagIfSet(b'10101', b'100') -> b'10001'
     *      removeBinaryFlagIfSet(b'10001', b'100') -> b'10001'
     * @param original
     * @param flag
     * @return
     */
    public static int removeBinaryFlag(int original, int flag) {
        if((original & flag) != flag) { // the flag is not set
            return original;
        }
        return original & ~flag;
    }
}
