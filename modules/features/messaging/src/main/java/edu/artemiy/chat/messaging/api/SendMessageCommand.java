package edu.artemiy.chat.messaging.api;

import java.util.Objects;
import java.util.UUID;

public record SendMessageCommand(ChatTargetRef chat, String bodyText, UUID parentMessageId) {

    public SendMessageCommand {
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(bodyText, "Message body is required.");
    }

    public SendMessageCommand(ChatTargetRef chat, String bodyText) {
        this(chat, bodyText, null);
    }
}
