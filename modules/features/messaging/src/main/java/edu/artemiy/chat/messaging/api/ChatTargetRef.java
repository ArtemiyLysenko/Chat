package edu.artemiy.chat.messaging.api;

import java.util.Objects;
import java.util.UUID;

public record ChatTargetRef(ChatTargetType type, UUID id) {

    public ChatTargetRef {
        Objects.requireNonNull(type, "Chat target type is required.");
        Objects.requireNonNull(id, "Chat target id is required.");
    }
}
