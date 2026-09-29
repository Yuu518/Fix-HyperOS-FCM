package io.github.Yuu.hyperosgmskeeper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** The deliberately narrow contract between this module and HyperOS. */
public final class HookContract {
    static final String GREEZE_MANAGER_CLASS =
            "com.miui.server.greeze.GreezeManagerService";
    static final String GMS_LIMIT_METHOD = "triggerGMSLimitAction";

    static final String NOTIFICATION_RECORD_CLASS =
            "com.android.server.notification.NotificationRecord";
    static final String COPY_RANKING_METHOD = "copyRankingInformation";
    static final String UPDATE_SYSTEM_CHANNEL_METHOD = "updateSystemNotificationChannel";

    static final String NOTIFICATION_SERVICE_CLASS =
            "com.android.server.notification.NotificationManagerService";
    static final String NOTIFICATION_INTERNAL_CLASS =
            "com.android.server.notification.NotificationManagerInternal";
    static final String LOCAL_SERVICES_CLASS = "com.android.server.LocalServices";
    static final String SERVICE_START_METHOD = "onStart";
    static final String CANCEL_ALL_METHOD = "cancelAllNotificationsInt";
    static final String RECEIVE_METHOD = "onReceive";

    private HookContract() {
    }

    static Method findServiceStartMethod(Class<?> serviceClass) throws NoSuchMethodException {
        return findVoidMethod(serviceClass, SERVICE_START_METHOD);
    }

    static List<Method> findCancelAllMethods(Class<?> serviceClass) throws NoSuchMethodException {
        List<Method> methods = new ArrayList<>();
        for (Method method : serviceClass.getDeclaredMethods()) {
            if (method.getName().equals(CANCEL_ALL_METHOD) && method.getReturnType() == void.class) {
                methods.add(method);
            }
        }
        if (methods.isEmpty()) {
            throw new NoSuchMethodException(serviceClass.getName() + "." + CANCEL_ALL_METHOD
                    + "() returning void");
        }
        return methods;
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
