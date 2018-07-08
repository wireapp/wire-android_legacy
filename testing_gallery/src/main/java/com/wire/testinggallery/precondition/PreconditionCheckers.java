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
package com.wire.testinggallery.precondition;

import android.app.Activity;
import android.provider.Settings;

import com.google.common.base.Supplier;

public class PreconditionCheckers {

    private Activity activity;

    public PreconditionCheckers(Activity activity) {
        this.activity = activity;
    }

    public Supplier<Boolean> permissionChecker() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return PreconditionsManager.checkRights(activity);
            }
        };
    }

    public Supplier<Boolean> directoryChecker() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return PreconditionsManager.checkDirectory(activity);
            }
        };
    }

    public Supplier<Boolean> getDocumentResolverChecker() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return PreconditionsManager.isDefaultGetDocumentResolver(activity.getApplicationContext());
            }
        };
    }

    public Supplier<Boolean> lockScreenChecker() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return PreconditionsManager.isLockScreenDisabled(activity);
            }
        };
    }

    public Supplier<Boolean> notificationAccessChecker() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return PreconditionsManager.hasNotificationAccess(activity);
            }
        };
    }

    public Supplier<Boolean> brightnessCheck() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                try {
                    return PreconditionsManager.getBrightness(activity) < 5;
                } catch (Settings.SettingNotFoundException e) {
                    return false;
                }
            }
        };
    }

    public Supplier<Boolean> stayAwakeCheck() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                try {
                    return PreconditionsManager.getStayAwake(activity) >= 3;
                } catch (Settings.SettingNotFoundException e) {
                    return false;
                }
            }
        };
    }

    public Supplier<Boolean> videoRecorderCheck() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return PreconditionsManager.isDefaultVideoRecorder(activity);
            }
        };
    }

    public Supplier<Boolean> defaultDocumentReceiverCheck() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return PreconditionsManager.isDefaultDocumentReceiver(activity);
            }
        };
    }
}
