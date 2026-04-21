package edu.artemiy.chat.messaging.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChatMessageEvent(
    UUID actorUserId,
    MessageEventType type,
    ChatMessage message,
    Instant occurredAt
) {

    public ChatMessageEvent {
        Objects.requireNonNull(actorUserId, "Actor user id is required.");
        Objects.requireNonNull(type, "Message event type is required.");
        Objects.requireNonNull(message, "Message is required.");
        Objects.requireNonNull(occurredAt, "Message event timestamp is required.");
    }
}
