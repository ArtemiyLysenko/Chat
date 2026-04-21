package edu.artemiy.chat.messaging.api;

import java.util.Objects;

public record SendMessageCommand(ChatTargetRef chat, String bodyText) {

    public SendMessageCommand {
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(bodyText, "Message body is required.");
    }
}
