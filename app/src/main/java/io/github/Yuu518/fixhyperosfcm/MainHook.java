package io.github.Yuu518.fixhyperosfcm;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.error.HookFailedError;

public final class MainHook extends XposedModule {
    private static final String TAG = "HyperOSFCM";
    private static final String GREEZE_SERVICE = "com.miui.server.greeze.GreezeManagerService";
    private static final String IMMOBULUS_MODE = "com.miui.server.greeze.AurogonImmobulusMode";
    private static final String DOMESTIC_POLICY = "com.miui.server.greeze.DomesticPolicyManager";

    private interface Installer {
        void install() throws ReflectiveOperationException;
    }

    private volatile ClassLoader systemLoader;

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        systemLoader = param.getClassLoader();
        install(systemLoader);
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        ClassLoader loader = systemLoader;
        if (loader == null) {
            return false;
        }
        param.setSavedInstanceState(loader);
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        List<HookHandle> oldHandles = param.getOldHookHandles();
        Set<String> installed = Set.of();
        try {
            ClassLoader loader = resolveLoader(param.getSavedInstanceState(), oldHandles);
            if (loader == null) {
                log(Log.ERROR, TAG, "Hot reload has no system class loader; reboot to restore hooks");
            } else {
                systemLoader = loader;
                installed = install(loader);
            }
        } finally {
            int removed = removeStale(oldHandles, installed);
            log(Log.INFO, TAG, "Hot reload complete; removed " + removed + " stale hooks");
        }
    }

    private static ClassLoader resolveLoader(Object savedState, List<HookHandle> oldHandles) {
        if (savedState instanceof ClassLoader loader) {
            return loader;
        }
        for (HookHandle handle : oldHandles) {
            ClassLoader loader = handle.getExecutable().getDeclaringClass().getClassLoader();
            if (loader != null) {
                return loader;
            }
        }
        return null;
    }

    private int removeStale(List<HookHandle> oldHandles, Set<String> installed) {
        List<String> oldIds = new ArrayList<>();
        for (HookHandle handle : oldHandles) {
            oldIds.add(handle.getId());
        }
        Set<String> stale = FcmPolicy.staleIds(oldIds, installed);
        int removed = 0;
        for (HookHandle handle : oldHandles) {
            String id = handle.getId();
            if (id != null && !stale.contains(id)) {
                continue;
            }
            try {
                handle.unhook();
                removed++;
            } catch (RuntimeException | HookFailedError error) {
                log(Log.ERROR, TAG, "Unable to remove stale hook " + id, error);
            }
        }
        return removed;
    }

    private Set<String> install(ClassLoader loader) {
        Set<String> installed = new LinkedHashSet<>();
        attempt(installed, FcmPolicy.ID_ALLOW_BROADCAST, () -> installAllowBroadcast(loader));
        attempt(installed, FcmPolicy.ID_GMS_LIMIT, () -> installGmsLimit(loader));
        attempt(installed, FcmPolicy.ID_QUICK_FREEZE, () -> installQuickFreeze(loader));
        attempt(installed, FcmPolicy.ID_GMS_NET_STATUS, () -> installGmsNetStatus(loader));
        attempt(installed, FcmPolicy.ID_DEFER_BROADCAST, () -> installDeferBroadcast(loader));
        log(Log.INFO, TAG, "Installed " + installed.size() + "/" + FcmPolicy.HOOK_IDS.size()
                + " hooks " + installed);
        return installed;
    }

    private void attempt(Set<String> installed, String id, Installer installer) {
        try {
            installer.install();
            installed.add(id);
        } catch (ReflectiveOperationException | RuntimeException | HookFailedError error) {
            log(Log.ERROR, TAG, "Unable to install hook " + id, error);
        }
    }

    private void installAllowBroadcast(ClassLoader loader) throws ReflectiveOperationException {
        Method method = find(loader, GREEZE_SERVICE, "isAllowBroadcast",
                int.class, String.class, int.class, String.class, String.class);
        hook(method).setId(FcmPolicy.ID_ALLOW_BROADCAST).intercept(chain ->
                FcmPolicy.allowsFrozenDelivery((String) chain.getArg(4)) ? Boolean.TRUE : chain.proceed());
    }

    private void installGmsLimit(ClassLoader loader) throws ReflectiveOperationException {
        Method method = find(loader, GREEZE_SERVICE, "triggerGMSLimitAction");
        hook(method).setId(FcmPolicy.ID_GMS_LIMIT).intercept(chain -> null);
    }

    private void installQuickFreeze(ClassLoader loader) throws ReflectiveOperationException {
        Method method = find(loader, IMMOBULUS_MODE, "triggerQuickFreeze", int.class, int.class);
        Method packageName = find(loader, IMMOBULUS_MODE, "getPackageNameFromUid", int.class);
        hook(method).setId(FcmPolicy.ID_QUICK_FREEZE).intercept(chain -> {
            Object name = packageName.invoke(chain.getThisObject(), chain.getArg(0));
            return FcmPolicy.isGmsPackage((String) name) ? null : chain.proceed();
        });
    }

    private void installGmsNetStatus(ClassLoader loader) throws ReflectiveOperationException {
        Method method = find(loader, GREEZE_SERVICE, "updateGmsNetStatus", boolean.class);
        hook(method).setId(FcmPolicy.ID_GMS_NET_STATUS).intercept(chain ->
                chain.proceed(new Object[]{Boolean.FALSE}));
    }

    private void installDeferBroadcast(ClassLoader loader) throws ReflectiveOperationException {
        Method method = find(loader, DOMESTIC_POLICY, "deferBroadcast", String.class);
        hook(method).setId(FcmPolicy.ID_DEFER_BROADCAST).intercept(chain ->
                FcmPolicy.isGcmConnectionAction((String) chain.getArg(0)) ? Boolean.FALSE : chain.proceed());
    }

    private static Method find(ClassLoader loader, String className, String name, Class<?>... parameters)
            throws ReflectiveOperationException {
        Method method = Class.forName(className, false, loader).getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
