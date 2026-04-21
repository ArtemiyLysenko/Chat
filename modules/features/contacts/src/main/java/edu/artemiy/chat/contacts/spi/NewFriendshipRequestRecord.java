package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.UUID;

public record NewFriendshipRequestRecord(
    UUID id,
    UUID requesterUserId,
    UUID recipientUserId,
    String messageText,
    Instant createdAt
) {
}
