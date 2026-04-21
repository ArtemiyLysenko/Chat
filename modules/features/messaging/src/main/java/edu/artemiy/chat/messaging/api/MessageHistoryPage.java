package edu.artemiy.chat.messaging.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MessageHistoryPage(ChatTargetRef chat, List<ChatMessage> items, UUID nextBeforeMessageId) {

    public MessageHistoryPage {
        Objects.requireNonNull(chat, "Chat target is required.");
        items = List.copyOf(items);
    }
}
