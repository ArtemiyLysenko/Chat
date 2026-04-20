package edu.artemiy.chat.messaging.api;

import java.time.Instant;
import java.util.UUID;

public record UnreadMarker(ChatTargetRef chat, UUID lastReadMessageId, Instant updatedAt) {
}
