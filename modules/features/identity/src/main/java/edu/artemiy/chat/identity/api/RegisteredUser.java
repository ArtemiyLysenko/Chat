package edu.artemiy.chat.identity.api;

import java.time.Instant;
import java.util.UUID;

public record RegisteredUser(
    UUID id,
    String email,
    String username,
    String displayName,
    Instant createdAt
) {
}
