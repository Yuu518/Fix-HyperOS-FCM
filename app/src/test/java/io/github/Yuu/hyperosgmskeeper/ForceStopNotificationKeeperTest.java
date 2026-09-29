package io.github.Yuu.hyperosgmskeeper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import com.android.server.LocalServices;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.notification.NotificationManagerService;

import org.junit.After;
import org.junit.Test;

public final class ForceStopNotificationKeeperTest {
    @After
    public void unregisterInternalService() {
        LocalServices.removeServiceForTest(NotificationManagerInternal.class);
    }

    @Test
    public void recognisesOnlyPackageRestart() {
        assertTrue(ForceStopNotificationKeeper.isPackageRestart(Intent.ACTION_PACKAGE_RESTARTED));
        assertFalse(ForceStopNotificationKeeper.isPackageRestart(
                "android.intent.action.QUERY_PACKAGE_RESTART"));
        assertFalse(ForceStopNotificationKeeper.isPackageRestart(Intent.ACTION_PACKAGE_REMOVED));
        assertFalse(ForceStopNotificationKeeper.isPackageRestart(Intent.ACTION_PACKAGE_CHANGED));
        assertFalse(ForceStopNotificationKeeper.isPackageRestart(null));
    }

    @Test
    public void ignoresArgumentsThatAreNotIntents() {
        assertNull(ForceStopNotificationKeeper.restartedPackage(null));
        assertNull(ForceStopNotificationKeeper.restartedPackage(Intent.ACTION_PACKAGE_RESTARTED));
    }

    @Test
    public void findsRunningServiceThroughInternalService() throws Exception {
        NotificationManagerService service = new NotificationManagerService();
        LocalServices.addService(NotificationManagerInternal.class, new FakeInternal(service));

        assertSame(service,
                ForceStopNotificationKeeper.findRunningService(NotificationManagerService.class));
    }

    @Test
    public void findsNoServiceBeforeItStarts() throws Exception {
        assertNull(ForceStopNotificationKeeper.findRunningService(NotificationManagerService.class));
    }

    @Test
    public void skipsReceiversThatAreNotCreated() throws Exception {
        assertTrue(ForceStopNotificationKeeper.findReceiverMethods(
                NotificationManagerService.class, new NotificationManagerService()).isEmpty());
    }

    private static final class FakeInternal implements NotificationManagerInternal {
        @SuppressWarnings("unused")
        private final NotificationManagerService outer;

        FakeInternal(NotificationManagerService outer) {
            this.outer = outer;
        }
    }
}
