package io.github.Yuu.hyperosgmskeeper;

import java.lang.reflect.Method;

/** The deliberately narrow contract between this module and HyperOS. */
public final class HookContract {
    static final String GREEZE_MANAGER_CLASS =
            "com.miui.server.greeze.GreezeManagerService";
    static final String GMS_LIMIT_METHOD = "triggerGMSLimitAction";

    private HookContract() {
    }

    static Method findLimitMethod(Class<?> serviceClass) throws NoSuchMethodException {
        Method method = serviceClass.getDeclaredMethod(GMS_LIMIT_METHOD);
        if (method.getReturnType() != void.class) {
            throw new NoSuchMethodException(serviceClass.getName() + "." + GMS_LIMIT_METHOD
                    + "() returns " + method.getReturnType().getName() + ", expected void");
        }
        return method;
    }
}
