package edu.artemiy.chat.attachments.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import edu.artemiy.chat.messaging.api.ChatTargetRef;

public record NewAttachmentMessageRecord(
    UUID id,
    ChatTargetRef chat,
    UUID authorUserId,
    String bodyText,
    Instant createdAt
) {

    public NewAttachmentMessageRecord {
        Objects.requireNonNull(id, "Message id is required.");
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(authorUserId, "Author user id is required.");
        Objects.requireNonNull(bodyText, "Message body is required.");
        Objects.requireNonNull(createdAt, "Message creation time is required.");
    }
}
