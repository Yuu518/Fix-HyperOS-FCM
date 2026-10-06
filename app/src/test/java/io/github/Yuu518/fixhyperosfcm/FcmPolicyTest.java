package io.github.Yuu518.fixhyperosfcm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FcmPolicyTest {
    @Test
    void hookIdsAreUnique() {
        assertEquals(FcmPolicy.HOOK_IDS.size(), new HashSet<>(FcmPolicy.HOOK_IDS).size());
    }

    @Test
    void pushActionMatchesOnlyC2dmReceive() {
        assertTrue(FcmPolicy.isPushAction("com.google.android.c2dm.intent.RECEIVE"));
        assertFalse(FcmPolicy.isPushAction("com.google.android.c2dm.intent.REGISTRATION"));
        assertFalse(FcmPolicy.isPushAction(null));
    }

    @Test
    void gcmConnectionActionsMatchDomesticDeferList() {
        assertTrue(FcmPolicy.isGcmConnectionAction("com.google.android.intent.action.GCM_RECONNECT"));
        assertTrue(FcmPolicy.isGcmConnectionAction("com.google.android.gcm.CONNECTED"));
        assertTrue(FcmPolicy.isGcmConnectionAction("com.google.android.gcm.DISCONNECTED"));
        assertTrue(FcmPolicy.isGcmConnectionAction("com.google.android.gms.gcm.HEARTBEAT_ALARM"));
        assertFalse(FcmPolicy.isGcmConnectionAction("android.intent.action.SCREEN_OFF"));
        assertFalse(FcmPolicy.isGcmConnectionAction(null));
    }

    @Test
    void frozenDeliveryCoversPushAndConnectionActions() {
        assertTrue(FcmPolicy.allowsFrozenDelivery("com.google.android.c2dm.intent.RECEIVE"));
        assertTrue(FcmPolicy.allowsFrozenDelivery("com.google.android.gms.gcm.HEARTBEAT_ALARM"));
        assertFalse(FcmPolicy.allowsFrozenDelivery("android.net.conn.CONNECTIVITY_CHANGE"));
        assertFalse(FcmPolicy.allowsFrozenDelivery(null));
    }

    @Test
    void gmsPackageMatchesExactly() {
        assertTrue(FcmPolicy.isGmsPackage("com.google.android.gms"));
        assertFalse(FcmPolicy.isGmsPackage("com.google.android.gms.policy_sidecar_aps"));
        assertFalse(FcmPolicy.isGmsPackage("com.google.android.gsf"));
        assertFalse(FcmPolicy.isGmsPackage(null));
    }

    @Test
    void staleIdsAreOldIdsMissingFromInstalled() {
        Set<String> stale = FcmPolicy.staleIds(
                Arrays.asList("a", "b", null, "c"), List.of("a", "c", "d"));
        assertEquals(Set.of("b"), stale);
    }

    @Test
    void everyOldIdIsStaleWhenNothingInstalled() {
        assertEquals(Set.of("a", "b"), FcmPolicy.staleIds(List.of("a", "b"), Set.of()));
    }
}
