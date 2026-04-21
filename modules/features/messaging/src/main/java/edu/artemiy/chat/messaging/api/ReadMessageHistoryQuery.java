package edu.artemiy.chat.messaging.api;

import java.util.Objects;
import java.util.UUID;

public record ReadMessageHistoryQuery(ChatTargetRef chat, UUID beforeMessageId, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public ReadMessageHistoryQuery {
        Objects.requireNonNull(chat, "Chat target is required.");
    }
}
