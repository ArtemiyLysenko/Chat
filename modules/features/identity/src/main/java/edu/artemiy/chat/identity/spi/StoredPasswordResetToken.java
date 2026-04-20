package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredPasswordResetToken(
    UUID id,
    UUID userId,
    String tokenHash,
    Instant createdAt,
    Instant expiresAt,
    Instant usedAt
) {
}
