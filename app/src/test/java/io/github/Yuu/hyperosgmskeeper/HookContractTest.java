package io.github.Yuu.hyperosgmskeeper;

import static org.junit.Assert.assertEquals;

import android.app.NotificationChannel;

import com.android.server.notification.NotificationManagerService;
import com.android.server.notification.NotificationRecord;

import java.lang.reflect.Method;
import java.util.List;
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

    @Test
    public void resolvesNotificationRecordChannelMethods() throws Exception {
        Method copyRanking = HookContract.findCopyRankingMethod(NotificationRecord.class);
        Method updateChannel = HookContract.findUpdateSystemChannelMethod(
                NotificationRecord.class, NotificationChannel.class);

        assertEquals("copyRankingInformation", copyRanking.getName());
        assertEquals(NotificationRecord.class, copyRanking.getParameterTypes()[0]);
        assertEquals("updateSystemNotificationChannel", updateChannel.getName());
        assertEquals(NotificationChannel.class, updateChannel.getParameterTypes()[0]);
    }

    @Test(expected = NoSuchMethodException.class)
    public void rejectsRecordWithoutCopyRankingMethod() throws Exception {
        HookContract.findCopyRankingMethod(FakeGreezeManager.class);
    }

    @Test
    public void resolvesOnlyVoidCancelAllOverloads() throws Exception {
        List<Method> methods = HookContract.findCancelAllMethods(NotificationManagerService.class);

        assertEquals(1, methods.size());
        assertEquals("cancelAllNotificationsInt", methods.get(0).getName());
        assertEquals(void.class, methods.get(0).getReturnType());
    }

    @Test(expected = NoSuchMethodException.class)
    public void rejectsServiceWithoutCancelAllMethod() throws Exception {
        HookContract.findCancelAllMethods(FakeGreezeManager.class);
    }

    @Test
    public void resolvesNotificationServiceStartMethod() throws Exception {
        Method method = HookContract.findServiceStartMethod(NotificationManagerService.class);

        assertEquals("onStart", method.getName());
        assertEquals(0, method.getParameterCount());
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
