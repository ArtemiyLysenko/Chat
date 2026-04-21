package edu.artemiy.chat.messaging.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import edu.artemiy.chat.messaging.api.ChatTargetRef;

public record StoredUnreadMarker(UUID id, UUID userId, ChatTargetRef chat, UUID lastReadMessageId, Instant updatedAt) {

    public StoredUnreadMarker {
        Objects.requireNonNull(id, "Unread marker id is required.");
        Objects.requireNonNull(userId, "Unread marker user id is required.");
        Objects.requireNonNull(chat, "Unread marker chat target is required.");
        Objects.requireNonNull(lastReadMessageId, "Unread marker last read message id is required.");
        Objects.requireNonNull(updatedAt, "Unread marker update time is required.");
    }
}
