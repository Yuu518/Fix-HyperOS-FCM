package com.android.server;

import java.util.HashMap;
import java.util.Map;

public final class LocalServices {
    private static final Map<Class<?>, Object> SERVICES = new HashMap<>();

    private LocalServices() {
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T> T getService(Class<T> type) {
        return (T) SERVICES.get(type);
    }

    public static synchronized <T> void addService(Class<T> type, T service) {
        SERVICES.put(type, service);
    }

    public static synchronized void removeServiceForTest(Class<?> type) {
        SERVICES.remove(type);
    }
}
