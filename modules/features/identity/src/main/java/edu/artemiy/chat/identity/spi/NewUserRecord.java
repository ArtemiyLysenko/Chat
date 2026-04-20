package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record NewUserRecord(
    UUID id,
    String email,
    String username,
    String displayName,
    String passwordHash,
    Instant createdAt
) {
}
