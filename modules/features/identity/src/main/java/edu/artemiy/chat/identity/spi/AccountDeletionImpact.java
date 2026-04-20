package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionImpact(
    UUID userId,
    Instant deletedAt
) {
}
