package io.github.Yuu.hyperosgmskeeper;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import org.junit.Test;

public final class HookContractTest {
    @Test
    public void resolvesOnlyNoArgGmsLimitMethod() throws Exception {
        Method method = HookContract.findLimitMethod(FakeGreezeManager.class);

        assertEquals("triggerGMSLimitAction", method.getName());
        assertEquals(0, method.getParameterCount());
        assertEquals(void.class, method.getReturnType());
    }

    @Test(expected = NoSuchMethodException.class)
    public void rejectsNonVoidGmsLimitMethod() throws Exception {
        HookContract.findLimitMethod(NonVoidGreezeManager.class);
    }

    @Test(expected = NoSuchMethodException.class)
    public void rejectsMissingNoArgGmsLimitMethod() throws Exception {
        HookContract.findLimitMethod(OverloadOnlyGreezeManager.class);
    }

    private static final class FakeGreezeManager {
        @SuppressWarnings("unused")
        private void triggerGMSLimitAction() {
        }

        @SuppressWarnings("unused")
        private void triggerGMSLimitAction(int uid) {
        }
    }

    private static final class NonVoidGreezeManager {
        @SuppressWarnings("unused")
        private boolean triggerGMSLimitAction() {
            return true;
        }
    }

    private static final class OverloadOnlyGreezeManager {
        @SuppressWarnings("unused")
        private void triggerGMSLimitAction(int uid) {
        }
    }
}
