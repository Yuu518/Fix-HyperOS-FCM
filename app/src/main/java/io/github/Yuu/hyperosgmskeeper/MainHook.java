package io.github.Yuu.hyperosgmskeeper;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
    static final String GMS_HOOK_ID = "block-gms-limit";
    static final String CHANNEL_HOOK_ID = "keep-alerting-channel";

    private volatile ClassLoader systemServerClassLoader;
    private volatile NotificationChannelKeeper channelKeeper;

    private final Hooker blocker = chain -> {
        log(Log.INFO, TAG, "blocked GreezeManagerService.triggerGMSLimitAction()");
        return null;
    };

    private final Hooker channelKeeperHooker = chain -> {
        Object result = chain.proceed();
        NotificationChannelKeeper keeper = channelKeeper;
        if (keeper != null) {
            try {
                String kept = keeper.keepAlertingChannel(chain.getThisObject(), chain.getArg(0));
                if (kept != null) {
                    log(Log.INFO, TAG, "kept alerting channel for " + kept);
                }
            } catch (Throwable error) {
                log(Log.WARN, TAG, "failed to keep alerting channel", error);
            }
        }
        return result;
    };

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        systemServerClassLoader = param.getClassLoader();
        for (HookTarget target : resolveTargets(systemServerClassLoader)) {
            try {
                hook(target.method).setId(target.id).intercept(target.hooker);
                log(Log.INFO, TAG, target.installedMessage);
            } catch (Throwable error) {
                log(Log.ERROR, TAG, "failed to install hook " + target.id, error);
            }
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

        List<HookTarget> pending = new ArrayList<>();
        if (systemServerClassLoader == null) {
            log(Log.ERROR, TAG, "failed to resolve hook targets after hot reload",
                    new IllegalStateException("system_server class loader was not handed over"));
        } else {
            pending.addAll(resolveTargets(systemServerClassLoader));
        }

        List<HookTarget> installed = new ArrayList<>();
        for (HookHandle handle : param.getOldHookHandles()) {
            HookTarget target = findTarget(pending, handle);
            if (target != null) {
                try {
                    handle.replaceHook(target.hooker);
                    pending.remove(target);
                    installed.add(target);
                    continue;
                } catch (Throwable error) {
                    log(Log.WARN, TAG, "failed to replace old hook " + target.id
                            + ", reinstalling", error);
                }
            }
            handle.unhook();
        }

        for (HookTarget target : pending) {
            try {
                hook(target.method).setId(target.id).intercept(target.hooker);
                installed.add(target);
            } catch (Throwable error) {
                log(Log.ERROR, TAG, "failed to install hook " + target.id
                        + " after hot reload", error);
            }
        }

        for (HookTarget target : installed) {
            log(Log.INFO, TAG, "hook " + target.id + " reinstalled in system_server after hot reload");
        }
    }

    private List<HookTarget> resolveTargets(ClassLoader classLoader) {
        List<HookTarget> targets = new ArrayList<>();
        try {
            targets.add(new HookTarget(GMS_HOOK_ID, resolveLimitMethod(classLoader), blocker,
                    "hook installed in system_server; scope is limited to system"));
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "failed to resolve hook " + GMS_HOOK_ID, error);
        }
        try {
            Class<?> recordClass = Class.forName(
                    HookContract.NOTIFICATION_RECORD_CLASS,
                    false,
                    classLoader
            );
            Method copyRanking = HookContract.findCopyRankingMethod(recordClass);
            channelKeeper = NotificationChannelKeeper.resolve(recordClass);
            targets.add(new HookTarget(CHANNEL_HOOK_ID, copyRanking, channelKeeperHooker,
                    "notification channel hook installed in system_server"));
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "failed to resolve hook " + CHANNEL_HOOK_ID, error);
        }
        return targets;
    }

    private static HookTarget findTarget(List<HookTarget> targets, HookHandle handle) {
        for (HookTarget target : targets) {
            if (target.method.equals(handle.getExecutable())) {
                return target;
            }
        }
        return null;
    }

    private static Method resolveLimitMethod(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> serviceClass = Class.forName(
                HookContract.GREEZE_MANAGER_CLASS,
                false,
                classLoader
        );
        return HookContract.findLimitMethod(serviceClass);
    }

    private static final class HookTarget {
        final String id;
        final Method method;
        final Hooker hooker;
        final String installedMessage;

        HookTarget(String id, Method method, Hooker hooker, String installedMessage) {
            this.id = id;
            this.method = method;
            this.hooker = hooker;
            this.installedMessage = installedMessage;
        }
    }
}
