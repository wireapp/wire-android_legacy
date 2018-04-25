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
