package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetNotification(
    UUID userId,
    String email,
    String rawToken,
    String baseUrl,
    Instant expiresAt
) {
}
