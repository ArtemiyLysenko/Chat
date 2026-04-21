package edu.artemiy.chat.presence.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SessionTabActivityRecord(
    UUID sessionId,
    String tabKey,
    Instant lastActivityAt,
    Instant lastPingAt
) {

    public SessionTabActivityRecord {
        Objects.requireNonNull(sessionId, "Session id is required.");
        Objects.requireNonNull(tabKey, "Tab key is required.");
        Objects.requireNonNull(lastPingAt, "Last ping timestamp is required.");
    }
}
