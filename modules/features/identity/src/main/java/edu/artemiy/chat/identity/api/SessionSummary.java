package edu.artemiy.chat.identity.api;

import java.time.Instant;

public record SessionSummary(
    String id,
    boolean current,
    Instant createdAt,
    Instant lastSeenAt,
    String userAgent,
    String ipAddress
) {
}
