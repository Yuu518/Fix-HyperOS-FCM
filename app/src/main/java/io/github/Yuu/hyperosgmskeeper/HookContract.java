package io.github.Yuu.hyperosgmskeeper;

import java.lang.reflect.Method;

/** The deliberately narrow contract between this module and HyperOS. */
public final class HookContract {
    static final String GREEZE_MANAGER_CLASS =
            "com.miui.server.greeze.GreezeManagerService";
    static final String GMS_LIMIT_METHOD = "triggerGMSLimitAction";

    static final String NOTIFICATION_RECORD_CLASS =
            "com.android.server.notification.NotificationRecord";
    static final String COPY_RANKING_METHOD = "copyRankingInformation";
    static final String UPDATE_SYSTEM_CHANNEL_METHOD = "updateSystemNotificationChannel";

    private HookContract() {
    }

    static Method findLimitMethod(Class<?> serviceClass) throws NoSuchMethodException {
        return findVoidMethod(serviceClass, GMS_LIMIT_METHOD);
    }

    static Method findCopyRankingMethod(Class<?> recordClass) throws NoSuchMethodException {
        return findVoidMethod(recordClass, COPY_RANKING_METHOD, recordClass);
    }

    static Method findUpdateSystemChannelMethod(Class<?> recordClass, Class<?> channelClass)
            throws NoSuchMethodException {
        return findVoidMethod(recordClass, UPDATE_SYSTEM_CHANNEL_METHOD, channelClass);
    }

    private static Method findVoidMethod(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        if (method.getReturnType() != void.class) {
            throw new NoSuchMethodException(owner.getName() + "." + name
                    + "() returns " + method.getReturnType().getName() + ", expected void");
        }
        return method;
    }
}
