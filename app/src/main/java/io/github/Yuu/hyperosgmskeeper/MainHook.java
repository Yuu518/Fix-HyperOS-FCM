package io.github.Yuu.hyperosgmskeeper;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModule;

/**
 * Blocks HyperOS' screen-off-only GMS limiting transaction at its source.
 *
 * <p>On the verified ROM, this method first removes com.google.android.gms from the
 * Aurogon allowlist and then queues quick-freeze for every running GMS UID. The quick-freeze
 * transaction subsequently disables the UID and removes its alarms. Returning before the
 * transaction begins preserves all three without weakening Greeze policy for other UIDs.</p>
 */
public final class MainHook extends XposedModule {
    private static final String TAG = "HyperOSGmsKeeper";
    private static final String HOOK_ID = "block-gms-limit";

    private final Hooker blocker = chain -> {
        log(Log.INFO, TAG, "blocked GreezeManagerService.triggerGMSLimitAction()");
        return null;
    };

    private volatile ClassLoader systemServerClassLoader;

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        systemServerClassLoader = param.getClassLoader();
        try {
            hook(resolveLimitMethod(systemServerClassLoader))
                    .setId(HOOK_ID)
                    .intercept(blocker);

            log(Log.INFO, TAG,
                    "hook installed in system_server; scope is limited to system");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "failed to install hook", error);
        }
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        param.setSavedInstanceState(systemServerClassLoader);
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        Object savedState = param.getSavedInstanceState();
        if (savedState instanceof ClassLoader) {
            systemServerClassLoader = (ClassLoader) savedState;
        }

        Method limitMethod = null;
        try {
            if (systemServerClassLoader == null) {
                throw new IllegalStateException("system_server class loader was not handed over");
            }
            limitMethod = resolveLimitMethod(systemServerClassLoader);
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "failed to resolve hook target after hot reload", error);
        }

        boolean installed = false;
        for (HookHandle handle : param.getOldHookHandles()) {
            if (!installed && limitMethod != null && limitMethod.equals(handle.getExecutable())) {
                try {
                    handle.replaceHook(blocker);
                    installed = true;
                    continue;
                } catch (Throwable error) {
                    log(Log.WARN, TAG, "failed to replace old hook, reinstalling", error);
                }
            }
            handle.unhook();
        }

        if (!installed && limitMethod != null) {
            try {
                hook(limitMethod).setId(HOOK_ID).intercept(blocker);
                installed = true;
            } catch (Throwable error) {
                log(Log.ERROR, TAG, "failed to install hook after hot reload", error);
            }
        }

        if (installed) {
            log(Log.INFO, TAG, "hook reinstalled in system_server after hot reload");
        }
    }

    private static Method resolveLimitMethod(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> serviceClass = Class.forName(
                HookContract.GREEZE_MANAGER_CLASS,
                false,
                classLoader
        );
        return HookContract.findLimitMethod(serviceClass);
    }
}
