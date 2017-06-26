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
package com.waz.zclient;

import com.waz.log.InternalLog;
import com.waz.zclient.controllers.IControllerFactory;

class WireUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private IControllerFactory controllerFactory;
    private Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    WireUncaughtExceptionHandler(IControllerFactory controllerFactory,
                                 Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler) {
        this.controllerFactory = controllerFactory;
        this.defaultUncaughtExceptionHandler = defaultUncaughtExceptionHandler;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            Throwable cause = throwable;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            StackTraceElement[] stack = cause.getStackTrace();

            String details = null;
            if (stack != null && stack.length > 0) {
                details = stack[0].toString();
            }
            controllerFactory.getUserPreferencesController().setCrashException(cause.getClass().getSimpleName(), details);
        } catch (Throwable ignored) {
        }
        if (defaultUncaughtExceptionHandler != null) {
            defaultUncaughtExceptionHandler.uncaughtException(thread, throwable);
        }
        InternalLog.flush();
    }
}
