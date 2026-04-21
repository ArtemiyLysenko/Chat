package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredPendingFriendRequestEntry(
    UUID requestId,
    UUID otherUserId,
    String otherUsername,
    String otherDisplayName,
    boolean otherDeleted,
    String messageText,
    Instant createdAt
) {
}
