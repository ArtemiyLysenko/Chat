package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredFriendContactEntry(
    UUID friendshipId,
    UUID otherUserId,
    String otherUsername,
    String otherDisplayName,
    boolean otherDeleted,
    UUID directDialogId,
    Instant friendsSince
) {
}
