/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.controllers;

import android.content.Context;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.controllers.loadtimelogger.LoadTimeLoggerController;

public class DefaultControllerFactory extends Base$$ControllerFactory {

    public DefaultControllerFactory(Context context) {
        super(context);
    }

    @Override
    protected void initLoadTimeLoggerController() {
        if (loadTimeLoggerController != null) {
            return;
        }
        if (BuildConfig.IS_LOADTIME_LOGGER_ENABLED) {
            loadTimeLoggerController = new LoadTimeLoggerController();
        } else {
            loadTimeLoggerController = new LoadTimeLoggerController.DisabledLoadTimeLoggerController();
        }
    }
}
