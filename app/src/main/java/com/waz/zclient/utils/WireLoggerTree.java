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

import com.waz.zclient.BuildConfig;
import timber.log.Timber;

public class WireLoggerTree extends Timber.DebugTree {

    private final int minLogLevel;

    public WireLoggerTree() {
        minLogLevel = BuildConfig.DEVELOPER_FEATURES_ENABLED ? 2 : 99;
    }

    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        if (priority < minLogLevel) {
            return;
        }
        super.log(priority, tag, message, t);
    }
}
