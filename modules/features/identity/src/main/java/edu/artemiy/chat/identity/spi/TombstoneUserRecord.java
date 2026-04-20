package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record TombstoneUserRecord(
    UUID userId,
    String email,
    String username,
    String displayName,
    Instant deletedAt
) {
}
