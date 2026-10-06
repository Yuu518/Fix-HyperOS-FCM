package io.github.Yuu518.fixhyperosfcm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class FcmPolicy {
    static final String ID_ALLOW_BROADCAST = "greeze.allowBroadcast";
    static final String ID_GMS_LIMIT = "greeze.gmsLimit";
    static final String ID_QUICK_FREEZE = "greeze.quickFreeze";
    static final String ID_GMS_NET_STATUS = "greeze.gmsNetStatus";
    static final String ID_DEFER_BROADCAST = "greeze.deferBroadcast";

    static final List<String> HOOK_IDS = List.of(
            ID_ALLOW_BROADCAST,
            ID_GMS_LIMIT,
            ID_QUICK_FREEZE,
            ID_GMS_NET_STATUS,
            ID_DEFER_BROADCAST);

    static final String GMS_PACKAGE = "com.google.android.gms";
    static final String PUSH_ACTION = "com.google.android.c2dm.intent.RECEIVE";

    private static final Set<String> GCM_CONNECTION_ACTIONS = Set.of(
            "com.google.android.intent.action.GCM_RECONNECT",
            "com.google.android.gcm.CONNECTED",
            "com.google.android.gcm.DISCONNECTED",
            "com.google.android.gms.gcm.HEARTBEAT_ALARM");

    private FcmPolicy() {
    }

    static boolean isPushAction(String action) {
        return PUSH_ACTION.equals(action);
    }

    static boolean isGcmConnectionAction(String action) {
        return action != null && GCM_CONNECTION_ACTIONS.contains(action);
    }

    static boolean allowsFrozenDelivery(String action) {
        return isPushAction(action) || isGcmConnectionAction(action);
    }

    static boolean isGmsPackage(String packageName) {
        return GMS_PACKAGE.equals(packageName);
    }

    static Set<String> staleIds(Collection<String> oldIds, Collection<String> installedIds) {
        Set<String> stale = new LinkedHashSet<>();
        for (String id : oldIds) {
            if (id != null && !installedIds.contains(id)) {
                stale.add(id);
            }
        }
        return stale;
    }
}
