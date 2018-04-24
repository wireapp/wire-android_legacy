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

    public Supplier<Boolean> documentResolverChecker() {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return PreconditionsManager.isDefaultDocumentResolver(activity.getApplicationContext());
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
                    return PreconditionsManager.getStayAwake(activity) == 7;
                } catch (Settings.SettingNotFoundException e) {
                    return false;
                }
            }
        };
    }
}
