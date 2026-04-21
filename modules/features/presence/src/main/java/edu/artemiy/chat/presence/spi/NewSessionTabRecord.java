package edu.artemiy.chat.presence.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NewSessionTabRecord(
    UUID sessionId,
    String tabKey,
    Instant connectedAt,
    Instant lastActivityAt,
    Instant lastPingAt
) {

    public NewSessionTabRecord {
        Objects.requireNonNull(sessionId, "Session id is required.");
        Objects.requireNonNull(tabKey, "Tab key is required.");
        Objects.requireNonNull(connectedAt, "Connection timestamp is required.");
        Objects.requireNonNull(lastPingAt, "Last ping timestamp is required.");
    }
}
