package edu.artemiy.chat.contacts.api;

import java.time.Instant;
import java.util.UUID;

public record PendingFriendRequestSummary(
    UUID requestId,
    ContactUserSummary user,
    String messageText,
    Instant createdAt
) {
}
