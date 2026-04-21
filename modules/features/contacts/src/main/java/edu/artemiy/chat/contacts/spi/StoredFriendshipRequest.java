package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredFriendshipRequest(
    UUID id,
    UUID requesterUserId,
    UUID recipientUserId,
    String messageText,
    FriendshipRequestStatus status,
    Instant createdAt,
    Instant respondedAt
) {
}
