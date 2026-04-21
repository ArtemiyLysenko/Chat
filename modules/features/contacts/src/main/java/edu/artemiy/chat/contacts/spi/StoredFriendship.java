package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredFriendship(
    UUID id,
    UUID userLowId,
    UUID userHighId,
    Instant createdAt
) {
}
