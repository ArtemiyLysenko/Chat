package edu.artemiy.chat.presence.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

import edu.artemiy.chat.presence.api.PresenceState;
import edu.artemiy.chat.presence.spi.StoredSessionTab;

public final class PresenceDerivationRules {

    public static final Duration CONNECTED_TIMEOUT = Duration.ofSeconds(45);
    public static final Duration ACTIVE_TIMEOUT = Duration.ofSeconds(60);

    private PresenceDerivationRules() {
    }

    public static PresenceState derive(Instant now, Collection<StoredSessionTab> tabs) {
        Objects.requireNonNull(now, "Current time is required.");
        Objects.requireNonNull(tabs, "Session tabs are required.");

        boolean hasConnectedTab = false;
        for (StoredSessionTab tab : tabs) {
            if (!isConnected(tab, now)) {
                continue;
            }
            hasConnectedTab = true;
            if (isActive(tab, now)) {
                return PresenceState.ONLINE;
            }
        }
        return hasConnectedTab ? PresenceState.AFK : PresenceState.OFFLINE;
    }

    private static boolean isConnected(StoredSessionTab tab, Instant now) {
        if (tab.closedAt() != null) {
            return false;
        }
        return tab.lastPingAt() != null && !tab.lastPingAt().isBefore(now.minus(CONNECTED_TIMEOUT));
    }

    private static boolean isActive(StoredSessionTab tab, Instant now) {
        return tab.lastActivityAt() != null && !tab.lastActivityAt().isBefore(now.minus(ACTIVE_TIMEOUT));
    }
}
