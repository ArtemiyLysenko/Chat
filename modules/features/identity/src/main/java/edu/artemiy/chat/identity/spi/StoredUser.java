package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredUser(
    UUID id,
    String email,
    String username,
    String displayName,
    String passwordHash,
    Instant createdAt,
    Instant deletedAt
) {
    public boolean deleted() {
        return deletedAt != null;
    }
}
