package edu.artemiy.chat.messaging.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UnreadMarkerUpdatedEvent(
    UUID userId,
    ChatTargetRef chat,
    UUID lastReadMessageId,
    Instant occurredAt
) {

    public UnreadMarkerUpdatedEvent {
        Objects.requireNonNull(userId, "User id is required.");
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(lastReadMessageId, "Last read message id is required.");
        Objects.requireNonNull(occurredAt, "Unread marker event timestamp is required.");
    }
}
