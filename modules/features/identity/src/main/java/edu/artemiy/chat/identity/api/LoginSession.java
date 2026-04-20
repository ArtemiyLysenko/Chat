package edu.artemiy.chat.identity.api;

import java.time.Instant;
import java.util.UUID;

public record LoginSession(
    UUID sessionId,
    Instant expiresAt,
    String username,
    String displayName
) {
}
