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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.text.format.DateFormat;
import com.waz.zclient.R;
import net.hockeyapp.android.CrashManagerListener;
import net.hockeyapp.android.ExceptionHandler;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Date;
import java.util.Locale;

public class ZTimeFormatter {

    public static String getSeparatorTime(@Nullable Context context, LocalDateTime now, LocalDateTime then, boolean is24HourFormat, ZoneId timeZone, boolean epocIsJustNow) {
        return getSeparatorTime(context, now, then, is24HourFormat, timeZone, epocIsJustNow, true);
    }

    public static String getSeparatorTime(@Nullable Context context, LocalDateTime now, LocalDateTime then, boolean is24HourFormat, ZoneId timeZone, boolean epocIsJustNow, boolean showWeekday) {
        return getSeparatorTime(context, now, then, is24HourFormat, timeZone, epocIsJustNow, showWeekday, false);
    }

    private static String getSeparatorTime(@Nullable Context context, LocalDateTime now, LocalDateTime then, boolean is24HourFormat, ZoneId timeZone, boolean epocIsJustNow, boolean showWeekday, boolean defaultLocale) {
        if (context == null) {
            return "";
        }
        Resources res;
        if (defaultLocale) {
            res = getEnglishResources(context);
        } else {
            res = context.getResources();
        }

        final boolean isLastTwoMins = now.minusMinutes(2).isBefore(then) || (epocIsJustNow && then.atZone(timeZone).toInstant().toEpochMilli() == 0);
        final boolean isLastSixtyMins = now.minusMinutes(60).isBefore(then);

        if (isLastTwoMins) {
            return res.getString(R.string.timestamp__just_now);
        } else if (isLastSixtyMins) {
            int minutes = (int) Duration.between(then, now).toMinutes();
            return res.getQuantityString(R.plurals.timestamp__x_minutes_ago, minutes, minutes);
        }

        final String time = is24HourFormat ? res.getString(R.string.timestamp_pattern__24h_format) :
                            res.getString(R.string.timestamp_pattern__12h_format);
        final boolean isSameDay = now.toLocalDate().atStartOfDay().isBefore(then);
        final boolean isThisYear = now.getYear() == then.getYear();
        final String pattern;
        if (isSameDay) {
            pattern = time;
        } else if (isThisYear) {
            if (showWeekday) {
                pattern = res.getString(R.string.timestamp_pattern__date_and_time__no_year, time);
            } else {
                pattern = res.getString(R.string.timestamp_pattern__date_and_time__no_year_no_weekday, time);
            }
        } else {
            if (showWeekday) {
                pattern = res.getString(R.string.timestamp_pattern__date_and_time__with_year, time);
            } else {
                pattern = res.getString(R.string.timestamp_pattern__date_and_time__with_year_no_weekday, time);
            }
        }
        try {
            return DateTimeFormatter.ofPattern(pattern).format(then.atZone(timeZone));
        } catch (Exception e) {
            ExceptionHandler.saveException(e, Thread.currentThread(),
                                           new CrashManagerListener() {
                                               @Override
                                               public String getDescription() {
                                                   return pattern;
                                               }
                                           });
            if (!defaultLocale) {
                return getSeparatorTime(context, now, then, is24HourFormat, timeZone, epocIsJustNow, showWeekday, true);
            } else {
                return "";
            }
        }
    }

    public static String getSingleMessageTime(Context context, Date date) {
        return getSingleMessageTime(context, date, false);
    }

    private static String getSingleMessageTime(Context context, Date date, boolean defaultLocale) {
        boolean is24HourFormat = DateFormat.is24HourFormat(context);
        Resources resources = defaultLocale ? getEnglishResources(context) : context.getResources();

        final String pattern = is24HourFormat ? resources.getString(R.string.timestamp_pattern__24h_format) :
                               resources.getString(R.string.timestamp_pattern__12h_format);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return formatter.format(DateConvertUtils.asLocalDateTime(date).atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            ExceptionHandler.saveException(e, Thread.currentThread(),
                                           new CrashManagerListener() {
                                               @Override
                                               public String getDescription() {
                                                   return pattern;
                                               }
                                           });
            if (!defaultLocale) {
                return getSingleMessageTime(context, date, true);
            } else {
                return "";
            }
        }
    }
    public static String getCurrentWeek(Context context) {
        return getCurrentWeek(context, false);
    }

    private static String getCurrentWeek(Context context, boolean defaultLocale) {
        final String pattern = context.getResources().getString(R.string.timestamp_pattern__week);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return formatter.format(DateConvertUtils.asLocalDateTime(Instant.now()).atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            ExceptionHandler.saveException(e, Thread.currentThread(),
                                           new CrashManagerListener() {
                                               @Override
                                               public String getDescription() {
                                                   return pattern;
                                               }
                                           });
            if (!defaultLocale) {
                return getCurrentWeek(context, true);
            } else {
                return "";
            }
        }
    }

    private static Resources getEnglishResources(Context context) {
        Configuration conf = context.getResources().getConfiguration();
        conf = new Configuration(conf);
        conf.setLocale(Locale.ENGLISH);
        Context localizedContext = context.createConfigurationContext(conf);
        return localizedContext.getResources();
    }

}
