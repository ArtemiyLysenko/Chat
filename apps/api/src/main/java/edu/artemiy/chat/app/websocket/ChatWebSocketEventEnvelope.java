package edu.artemiy.chat.app.websocket;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import edu.artemiy.chat.messaging.api.ChatTargetRef;

@JsonInclude(JsonInclude.Include.NON_NULL)
record ChatWebSocketEventEnvelope(
    UUID eventId,
    String type,
    Instant occurredAt,
    ChatTargetRef chat,
    Object payload
) {

    ChatWebSocketEventEnvelope {
        Objects.requireNonNull(eventId, "Event id is required.");
        Objects.requireNonNull(type, "Event type is required.");
        Objects.requireNonNull(occurredAt, "Event timestamp is required.");
    }
}
