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

import com.google.common.base.Supplier;

public class PreconditionFixers {

    private Activity activity;

    public PreconditionFixers(Activity activity) {
        this.activity = activity;
    }

    public Supplier<Void> permissionsFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.requestSilentlyRights(activity);
                return null;
            }
        };
    }

    public Supplier<Void> directoryFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.createDirectory(activity);
                return null;
            }
        };
    }

    public Supplier<Void> getDocumentResolverFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.fixDefaultGetDocumentResolver(activity);
                return null;
            }
        };
    }

    public Supplier<Void> lockScreenFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.fixLockScreen(activity);
                return null;
            }
        };
    }

    public Supplier<Void> notificationAccessFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.fixNotificationAccess(activity);
                return null;
            }
        };
    }

    public Supplier<Void> brightnessFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.setMinBrightness(activity);
                return null;
            }
        };
    }

    public Supplier<Void> stayAwakeFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.goToStayAwake(activity);
                return null;
            }
        };
    }

    public Supplier<Void> videoRecorderFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.fixDefaultVideoRecorder(activity);
                return null;
            }
        };
    }

    public Supplier<Void> defaultDocumentReceiverFix() {
        return new Supplier<Void>() {
            @Override
            public Void get() {
                PreconditionsManager.fixDefaultDocumentReceiver(activity);
                return null;
            }
        };
    }
}
