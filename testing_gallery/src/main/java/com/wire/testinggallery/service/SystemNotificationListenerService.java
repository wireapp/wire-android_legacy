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
package com.wire.testinggallery.service;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.wire.testinggallery.store.NotificationMessage;

public class SystemNotificationListenerService extends NotificationListenerService {
    private SystemNotificationListenerReceiver receiver;

    private static final String ACTION_NAME = "com.wire.testinggallery.notification";
    private static final String PACKAGE_WIRE = "wire";
    private static final String PACKAGE_WAZ = "waz";

    private static final String NOTIFICATION_TITLE = "android.title";
    private static final String NOTIFICATION_TEXT = "android.text";
    private static final String NOTIFICATION_TEXTLINES = "android.textLines";

    private static final String COMMAND = "command";
    private static final String COMMAND_GET = "get";
    private static final String COMMAND_CLEAR = "clear";
    private static final String COMMAND_REPLY = "reply";
    private static final String COMMAND_CALL = "call";
    private static final int CALL_ACTION = 0;
    private static final int REPLY_ACTION = 1;

    private NotificationMessage recentNotificationMessage;


    @Override
    public void onCreate() {
        super.onCreate();
        receiver = new SystemNotificationListenerReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_NAME);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        String packageName = statusBarNotification.getPackageName();
        if (isWireNotification(packageName)) {
            recentNotificationMessage = getNotificationMessage(statusBarNotification.getId(),
                                                               statusBarNotification.getNotification());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        String packageName = statusBarNotification.getPackageName();
        if (isWireNotification(packageName) && recentNotificationMessage != null) {
            recentNotificationMessage = null;
        }
    }

    private boolean isWireNotification(String packageName) {
        return packageName.contains(PACKAGE_WIRE) || packageName.contains(PACKAGE_WAZ);
    }

    private static NotificationMessage getNotificationMessage(int id, Notification notification) {
        String title = notification.extras.getString(NOTIFICATION_TITLE);
        CharSequence[] sequence = notification.extras.getCharSequenceArray(NOTIFICATION_TEXTLINES);

        String text = "";
        Object textObj = notification.extras.get(NOTIFICATION_TEXT);
        if (textObj != null) {
            text = textObj.toString();
        }
        return new NotificationMessage(id, title, text, sequence, notification);
    }

    class SystemNotificationListenerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra(COMMAND);
            switch (command) {
                case COMMAND_CLEAR:
                    SystemNotificationListenerService.this.cancelAllNotifications();
                    setResultCode(Activity.RESULT_OK);
                    break;
                case COMMAND_GET:
                    setResultCode(Activity.RESULT_OK);
                    if (recentNotificationMessage != null) {
                        setResultData(recentNotificationMessage.toJsonString());
                    } else {
                        setResultData("");
                    }
                    break;
                case COMMAND_REPLY:
                    try {
                        recentNotificationMessage.getNotification().actions[REPLY_ACTION].actionIntent.send();
                        setResultCode(Activity.RESULT_OK);
                    } catch (PendingIntent.CanceledException e) {
                        setResultCode(Activity.RESULT_CANCELED);
                    }
                    break;
                case COMMAND_CALL:
                    try {
                        recentNotificationMessage.getNotification().actions[CALL_ACTION].actionIntent.send();
                        setResultCode(Activity.RESULT_OK);
                    } catch (PendingIntent.CanceledException e) {
                        setResultCode(Activity.RESULT_CANCELED);
                    }
                    break;
                default:
                    throw new RuntimeException(String.format("Cannot identify the notification command [%s]", command));
            }
        }
    }

}
