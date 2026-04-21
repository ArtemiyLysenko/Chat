package edu.artemiy.chat.messaging.api;

import java.util.Objects;
import java.util.UUID;

public record EditMessageCommand(UUID messageId, String bodyText) {

    public EditMessageCommand {
        Objects.requireNonNull(messageId, "Message id is required.");
        Objects.requireNonNull(bodyText, "Message body is required.");
    }
}
