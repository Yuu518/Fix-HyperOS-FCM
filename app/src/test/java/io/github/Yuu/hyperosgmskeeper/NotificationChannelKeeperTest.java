package io.github.Yuu.hyperosgmskeeper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;

import com.android.server.notification.NotificationRecord;

import org.junit.Test;

public final class NotificationChannelKeeperTest {
    private static final int HIGH = NotificationManager.IMPORTANCE_HIGH;
    private static final int DEFAULT = NotificationManager.IMPORTANCE_DEFAULT;
    private static final int LOW = NotificationManager.IMPORTANCE_LOW;
    private static final int MIN = NotificationManager.IMPORTANCE_MIN;
    private static final int NONE = NotificationManager.IMPORTANCE_NONE;

    @Test
    public void keepsAlertingChannelWhenUpdateMovesToSilentChannel() {
        assertTrue(keep("messages", HIGH, "silent", LOW, false, 0));
        assertTrue(keep("messages", DEFAULT, "silent", MIN, false, 0));
    }

    @Test
    public void ignoresUpdatesThatStayInSameChannel() {
        assertFalse(keep("messages", HIGH, "messages", LOW, false, 0));
    }

    @Test
    public void ignoresUpdatesThatDoNotLowerBelowDefault() {
        assertFalse(keep("messages", HIGH, "other", DEFAULT, false, 0));
        assertFalse(keep("quiet", LOW, "silent", MIN, false, 0));
    }

    @Test
    public void respectsChannelsTheUserSilenced() {
        assertFalse(keep("messages", HIGH, "silent", LOW, true, 0));
    }

    @Test
    public void ignoresBlockedChannels() {
        assertFalse(keep("messages", HIGH, "blocked", NONE, false, 0));
    }

    @Test
    public void ignoresOngoingAndForegroundNotifications() {
        assertFalse(keep("messages", HIGH, "silent", LOW, false, Notification.FLAG_ONGOING_EVENT));
        assertFalse(keep("messages", HIGH, "silent", LOW, false,
                Notification.FLAG_FOREGROUND_SERVICE));
        assertFalse(keep("messages", HIGH, "silent", LOW, false, 0x00008000));
    }

    @Test
    public void resolvesAgainstNotificationRecord() throws Exception {
        assertNotNull(NotificationChannelKeeper.resolve(NotificationRecord.class));
    }

    private static boolean keep(String previousId, int previousImportance, String incomingId,
            int incomingImportance, boolean userSet, int flags) {
        return NotificationChannelKeeper.shouldKeepChannel(previousId, previousImportance,
                incomingId, incomingImportance, userSet, flags);
    }
}
