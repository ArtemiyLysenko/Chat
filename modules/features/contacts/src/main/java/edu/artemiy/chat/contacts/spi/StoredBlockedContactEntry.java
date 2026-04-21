package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredBlockedContactEntry(
    UUID otherUserId,
    String otherUsername,
    String otherDisplayName,
    boolean otherDeleted,
    Instant blockedAt
) {
}
