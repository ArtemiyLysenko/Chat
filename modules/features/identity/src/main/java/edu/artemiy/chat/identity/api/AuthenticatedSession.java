package edu.artemiy.chat.identity.api;

import java.time.Instant;
import java.util.UUID;

public record AuthenticatedSession(
    UUID userId,
    UUID sessionId,
    String username,
    String displayName,
    Instant expiresAt
) {
}
