package edu.artemiy.chat.identity.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record SessionRevokedEvent(
    UUID userId,
    Set<UUID> sessionIds,
    Instant occurredAt
) {

    public SessionRevokedEvent {
        Objects.requireNonNull(userId, "User id is required.");
        sessionIds = Set.copyOf(sessionIds);
        Objects.requireNonNull(occurredAt, "Session revocation timestamp is required.");
    }
}
