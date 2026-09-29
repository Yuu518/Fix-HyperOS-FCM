package io.github.Yuu.hyperosgmskeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import com.android.server.notification.NotificationRecord;
import com.miui.server.greeze.GreezeManagerService;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam;
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam;
import org.junit.Test;

public final class MainHookHotReloadTest {
    private static final ClassLoader SYSTEM_SERVER = GreezeManagerService.class.getClassLoader();

    @Test
    public void installsBothHooksOnSystemServerStart() throws Throwable {
        FakeFramework framework = new FakeFramework();
        MainHook module = framework.attach(new MainHook());

        module.onSystemServerStarting(() -> SYSTEM_SERVER);

        assertEquals(2, framework.installed.size());
        FakeHandle gmsHook = framework.find(MainHook.GMS_HOOK_ID);
        assertEquals(limitMethod(), gmsHook.executable);
        assertNull(gmsHook.hooker.intercept(null));
        assertEquals(copyRankingMethod(), framework.find(MainHook.CHANNEL_HOOK_ID).executable);
    }

    @Test
    public void channelHookAlwaysProceeds() throws Throwable {
        FakeFramework framework = new FakeFramework();
        MainHook module = framework.attach(new MainHook());
        module.onSystemServerStarting(() -> SYSTEM_SERVER);
        FakeChain chain = new FakeChain(new NotificationRecord(), new NotificationRecord());

        Object result = framework.find(MainHook.CHANNEL_HOOK_ID).hooker.intercept(chain.proxy());

        assertTrue(chain.proceeded);
        assertSame(FakeChain.PROCEED_RESULT, result);
    }

    @Test
    public void hotReloadingHandsOverSystemServerClassLoader() {
        MainHook module = new FakeFramework().attach(new MainHook());
        module.onSystemServerStarting(() -> SYSTEM_SERVER);
        Reloading reloading = new Reloading();

        assertTrue(module.onHotReloading(reloading));
        assertSame(SYSTEM_SERVER, reloading.savedState);
    }

    @Test
    public void hotReloadReplacesOldHooksAndDropsOthers() throws Throwable {
        FakeFramework oldFramework = new FakeFramework();
        MainHook oldModule = oldFramework.attach(new MainHook());
        oldModule.onSystemServerStarting(() -> SYSTEM_SERVER);
        FakeHandle oldGmsHook = oldFramework.find(MainHook.GMS_HOOK_ID);
        FakeHandle oldChannelHook = oldFramework.find(MainHook.CHANNEL_HOOK_ID);
        FakeHandle strayHook = new FakeHandle(quickFreezeMethod(), null, chain -> null);
        Reloading reloading = new Reloading();
        oldModule.onHotReloading(reloading);

        FakeFramework newFramework = new FakeFramework();
        MainHook newModule = newFramework.attach(new MainHook());
        newModule.onHotReloaded(new Reloaded(reloading.savedState,
                List.of(oldGmsHook, oldChannelHook, strayHook)));

        assertNotNull(oldGmsHook.replacement);
        assertNotSame(oldGmsHook.hooker, oldGmsHook.replacement.hooker);
        assertNull(oldGmsHook.replacement.hooker.intercept(null));
        assertFalse(oldGmsHook.unhooked);
        assertNotNull(oldChannelHook.replacement);
        assertNotSame(oldChannelHook.hooker, oldChannelHook.replacement.hooker);
        assertFalse(oldChannelHook.unhooked);
        assertTrue(strayHook.unhooked);
        assertTrue(newFramework.installed.isEmpty());
    }

    @Test
    public void hotReloadInstallsHooksWhenOldGenerationHadNone() throws Exception {
        FakeFramework framework = new FakeFramework();
        MainHook module = framework.attach(new MainHook());

        module.onHotReloaded(new Reloaded(SYSTEM_SERVER, List.of()));

        assertEquals(2, framework.installed.size());
        assertEquals(limitMethod(), framework.find(MainHook.GMS_HOOK_ID).executable);
        assertEquals(copyRankingMethod(), framework.find(MainHook.CHANNEL_HOOK_ID).executable);
    }

    @Test
    public void hotReloadInstallsOnlyMissingHook() throws Exception {
        FakeFramework framework = new FakeFramework();
        MainHook module = framework.attach(new MainHook());
        FakeHandle oldGmsHook = new FakeHandle(limitMethod(), MainHook.GMS_HOOK_ID, chain -> null);

        module.onHotReloaded(new Reloaded(SYSTEM_SERVER, List.of(oldGmsHook)));

        assertNotNull(oldGmsHook.replacement);
        assertEquals(1, framework.installed.size());
        assertEquals(copyRankingMethod(), framework.installed.get(0).executable);
    }

    @Test
    public void hotReloadWithoutClassLoaderUnhooksOldHooks() throws Exception {
        FakeFramework framework = new FakeFramework();
        MainHook module = framework.attach(new MainHook());
        FakeHandle oldHook = new FakeHandle(limitMethod(), null, chain -> null);

        module.onHotReloaded(new Reloaded(null, List.of(oldHook)));

        assertTrue(oldHook.unhooked);
        assertNull(oldHook.replacement);
        assertTrue(framework.installed.isEmpty());
    }

    @Test
    public void hotReloadFallsBackToNewHookWhenReplaceFails() throws Exception {
        FakeFramework framework = new FakeFramework();
        MainHook module = framework.attach(new MainHook());
        FakeHandle oldHook = new FakeHandle(limitMethod(), null, chain -> null);
        oldHook.invalid = true;

        module.onHotReloaded(new Reloaded(SYSTEM_SERVER, List.of(oldHook)));

        assertTrue(oldHook.unhooked);
        assertEquals(2, framework.installed.size());
        assertEquals(limitMethod(), framework.find(MainHook.GMS_HOOK_ID).executable);
    }

    private static Method limitMethod() throws NoSuchMethodException {
        return GreezeManagerService.class.getDeclaredMethod("triggerGMSLimitAction");
    }

    private static Method copyRankingMethod() throws NoSuchMethodException {
        return NotificationRecord.class.getDeclaredMethod(
                "copyRankingInformation", NotificationRecord.class);
    }

    private static Method quickFreezeMethod() throws NoSuchMethodException {
        return GreezeManagerService.class.getDeclaredMethod(
                "triggerQuickFreeze", int.class, int.class);
    }

    private static final class FakeFramework implements InvocationHandler {
        final List<FakeHandle> installed = new ArrayList<>();

        MainHook attach(MainHook module) {
            XposedInterface base = (XposedInterface) Proxy.newProxyInstance(
                    XposedInterface.class.getClassLoader(),
                    new Class<?>[]{XposedInterface.class},
                    this);
            module.attachFramework(base, () -> {
            });
            return module;
        }

        FakeHandle find(String id) {
            for (FakeHandle handle : installed) {
                if (id.equals(handle.id)) {
                    return handle;
                }
            }
            throw new AssertionError("no hook installed with id " + id);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("hook")) {
                return new FakeBuilder(this, (Executable) args[0]);
            }
            return null;
        }
    }

    private static final class FakeBuilder implements XposedInterface.HookBuilder {
        private final FakeFramework framework;
        private final Executable executable;
        private String id;

        FakeBuilder(FakeFramework framework, Executable executable) {
            this.framework = framework;
            this.executable = executable;
        }

        @Override
        public XposedInterface.HookBuilder setPriority(int priority) {
            return this;
        }

        @Override
        public XposedInterface.HookBuilder setExceptionMode(XposedInterface.ExceptionMode mode) {
            return this;
        }

        @Override
        public XposedInterface.HookBuilder setId(String id) {
            this.id = id;
            return this;
        }

        @Override
        public HookHandle intercept(Hooker hooker) {
            FakeHandle handle = new FakeHandle(executable, id, hooker);
            framework.installed.add(handle);
            return handle;
        }
    }

    private static final class FakeHandle implements HookHandle {
        final Executable executable;
        final String id;
        final Hooker hooker;
        boolean unhooked;
        boolean invalid;
        FakeHandle replacement;

        FakeHandle(Executable executable, String id, Hooker hooker) {
            this.executable = executable;
            this.id = id;
            this.hooker = hooker;
        }

        @Override
        public Executable getExecutable() {
            return executable;
        }

        @Override
        public void unhook() {
            unhooked = true;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public HookHandle replaceHook(Hooker hooker) {
            if (invalid) {
                throw new IllegalStateException("hook handle is no longer valid");
            }
            replacement = new FakeHandle(executable, id, hooker);
            return replacement;
        }
    }

    private static final class FakeChain implements InvocationHandler {
        static final Object PROCEED_RESULT = new Object();

        private final Object thisObject;
        private final Object previous;
        boolean proceeded;

        FakeChain(Object thisObject, Object previous) {
            this.thisObject = thisObject;
            this.previous = previous;
        }

        Chain proxy() {
            return (Chain) Proxy.newProxyInstance(
                    Chain.class.getClassLoader(),
                    new Class<?>[]{Chain.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "proceed":
                    proceeded = true;
                    return PROCEED_RESULT;
                case "getThisObject":
                    return thisObject;
                case "getArg":
                    return previous;
                default:
                    return null;
            }
        }
    }

    private static final class Reloading implements HotReloadingParam {
        Object savedState;

        @Override
        public Bundle getExtras() {
            return null;
        }

        @Override
        public void setSavedInstanceState(Object outState) {
            savedState = outState;
        }
    }

    private static final class Reloaded implements HotReloadedParam {
        private final Object savedState;
        private final List<HookHandle> oldHooks;

        Reloaded(Object savedState, List<HookHandle> oldHooks) {
            this.savedState = savedState;
            this.oldHooks = oldHooks;
        }

        @Override
        public Bundle getExtras() {
            return null;
        }

        @Override
        public Object getSavedInstanceState() {
            return savedState;
        }

        @Override
        public List<HookHandle> getOldHookHandles() {
            return oldHooks;
        }

        @Override
        public boolean isSystemServer() {
            return true;
        }

        @Override
        public String getProcessName() {
            return "system_server";
        }
    }
}
