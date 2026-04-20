package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record NewSessionRecord(
    UUID id,
    UUID userId,
    Instant createdAt,
    Instant lastSeenAt,
    Instant expiresAt,
    String userAgent,
    String ipAddress
) {
}
