package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredSession(
    UUID id,
    UUID userId,
    Instant createdAt,
    Instant lastSeenAt,
    Instant expiresAt,
    Instant revokedAt,
    String userAgent,
    String ipAddress
) {
}
