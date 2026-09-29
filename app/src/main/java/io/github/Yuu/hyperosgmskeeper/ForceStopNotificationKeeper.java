package io.github.Yuu.hyperosgmskeeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class ForceStopNotificationKeeper {
    private ForceStopNotificationKeeper() {
    }

    static boolean isPackageRestart(String action) {
        return Intent.ACTION_PACKAGE_RESTARTED.equals(action);
    }

    static String restartedPackage(Object intent) {
        if (!(intent instanceof Intent) || !isPackageRestart(((Intent) intent).getAction())) {
            return null;
        }
        Uri data = ((Intent) intent).getData();
        String packageName = data == null ? null : data.getSchemeSpecificPart();
        return packageName == null ? "" : packageName;
    }

    static Object findRunningService(Class<?> serviceClass) throws ReflectiveOperationException {
        ClassLoader classLoader = serviceClass.getClassLoader();
        Class<?> localServices = Class.forName(
                HookContract.LOCAL_SERVICES_CLASS, false, classLoader);
        Class<?> internalClass = Class.forName(
                HookContract.NOTIFICATION_INTERNAL_CLASS, false, classLoader);
        Object internal = localServices.getMethod("getService", Class.class)
                .invoke(null, internalClass);
        if (internal == null) {
            return null;
        }
        for (Field field : internal.getClass().getDeclaredFields()) {
            if (serviceClass.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field.get(internal);
            }
        }
        return null;
    }

    static List<Method> findReceiverMethods(Class<?> serviceClass, Object service)
            throws IllegalAccessException {
        List<Method> methods = new ArrayList<>();
        for (Field field : serviceClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    || !BroadcastReceiver.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object receiver = field.get(service);
            if (receiver == null) {
                continue;
            }
            try {
                Method method = receiver.getClass().getDeclaredMethod(
                        HookContract.RECEIVE_METHOD, Context.class, Intent.class);
                if (!methods.contains(method)) {
                    methods.add(method);
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return methods;
    }
}
