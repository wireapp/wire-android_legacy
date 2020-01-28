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
package com.waz.zclient.utils;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

/**
 * Utilities for conversion between the old and new JDK date types
 * (between {@code java.util.Date} and {@code org.threeten.bp.*}).
 * <p/>
 * <p/>
 * All methods are null-safe.
 */
public class DateConvertUtils {

    /**
     * Calls {@link #asZonedDateTime(Instant, ZoneId)} with the system default time zone.
     */
    public static ZonedDateTime asZonedDateTime(Instant instant) {
        return asZonedDateTime(instant, ZoneId.systemDefault());
    }

    /**
     * Creates {@link ZonedDateTime} from {@code java.util.Date} or it's subclasses. Null-safe.
     */
    public static ZonedDateTime asZonedDateTime(Instant date, ZoneId zone) {
        if (date == null) {
            return null;
        } else {
            return date.atZone(zone);
        }
    }
}
