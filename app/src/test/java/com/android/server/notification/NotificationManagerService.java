package com.android.server.notification;

import android.content.BroadcastReceiver;

public class NotificationManagerService {
    private final BroadcastReceiver mPackageIntentReceiver = null;

    public void onStart() {
    }

    void cancelAllNotificationsInt(int callingUid, int callingPid, String pkg, String channelId,
            int mustHaveFlags, int mustNotHaveFlags, int userId, int reason) {
    }

    boolean cancelAllNotificationsInt(String pkg) {
        return false;
    }
}
