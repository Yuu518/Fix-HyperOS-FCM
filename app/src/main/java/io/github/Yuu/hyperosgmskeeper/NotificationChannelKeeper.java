package io.github.Yuu.hyperosgmskeeper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import java.lang.reflect.Method;
import java.util.Objects;

final class NotificationChannelKeeper {
    private static final int FLAG_USER_INITIATED_JOB = 0x00008000;
    private static final int PINNED_FLAGS = Notification.FLAG_ONGOING_EVENT
            | Notification.FLAG_FOREGROUND_SERVICE
            | FLAG_USER_INITIATED_JOB;

    private final Method getChannel;
    private final Method getNotification;
    private final Method getKey;
    private final Method setPostSilently;
    private final Method updateSystemChannel;

    private NotificationChannelKeeper(Class<?> recordClass) throws NoSuchMethodException {
        getChannel = accessible(recordClass.getDeclaredMethod("getChannel"));
        getNotification = accessible(recordClass.getDeclaredMethod("getNotification"));
        getKey = accessible(recordClass.getDeclaredMethod("getKey"));
        setPostSilently = accessible(
                recordClass.getDeclaredMethod("setPostSilently", boolean.class));
        updateSystemChannel = accessible(
                HookContract.findUpdateSystemChannelMethod(recordClass, NotificationChannel.class));
    }

    static NotificationChannelKeeper resolve(Class<?> recordClass) throws NoSuchMethodException {
        return new NotificationChannelKeeper(recordClass);
    }

    static boolean shouldKeepChannel(String previousChannelId, int previousImportance,
            String incomingChannelId, int incomingImportance, boolean incomingUserSet,
            int incomingFlags) {
        return !Objects.equals(previousChannelId, incomingChannelId)
                && !incomingUserSet
                && previousImportance >= NotificationManager.IMPORTANCE_DEFAULT
                && incomingImportance > NotificationManager.IMPORTANCE_NONE
                && incomingImportance < NotificationManager.IMPORTANCE_DEFAULT
                && (incomingFlags & PINNED_FLAGS) == 0;
    }

    String keepAlertingChannel(Object incoming, Object previous) throws ReflectiveOperationException {
        NotificationChannel previousChannel = (NotificationChannel) getChannel.invoke(previous);
        NotificationChannel incomingChannel = (NotificationChannel) getChannel.invoke(incoming);
        Notification notification = (Notification) getNotification.invoke(incoming);
        if (previousChannel == null || incomingChannel == null || notification == null
                || !shouldKeepChannel(previousChannel.getId(), previousChannel.getImportance(),
                        incomingChannel.getId(), incomingChannel.getImportance(),
                        incomingChannel.hasUserSetImportance(), notification.flags)) {
            return null;
        }

        notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
        setPostSilently.invoke(incoming, true);
        updateSystemChannel.invoke(incoming, previousChannel);
        return getKey.invoke(incoming) + ": " + incomingChannel.getId()
                + " -> " + previousChannel.getId();
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }
}
