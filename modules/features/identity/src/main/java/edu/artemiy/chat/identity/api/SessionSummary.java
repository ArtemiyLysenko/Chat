package edu.artemiy.chat.identity.api;

import java.time.Instant;
import java.util.UUID;

public record SessionSummary(
    UUID id,
    boolean current,
    Instant createdAt,
    Instant lastSeenAt,
    String userAgent,
    String ipAddress
) {
}
