package edu.artemiy.chat.messaging.api;

import java.util.Objects;
import java.util.UUID;

public record AdvanceReadMarkerCommand(ChatTargetRef chat, UUID lastReadMessageId) {

    public AdvanceReadMarkerCommand {
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(lastReadMessageId, "Last read message id is required.");
    }
}
