package edu.artemiy.chat.attachments.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import edu.artemiy.chat.messaging.api.ChatTargetRef;

public record NewAttachmentRecord(
    UUID id,
    String storageKey,
    String originalName,
    String mediaType,
    long sizeBytes,
    String sha256,
    UUID uploadedByUserId,
    ChatTargetRef chat,
    Instant createdAt
) {

    public NewAttachmentRecord {
        Objects.requireNonNull(id, "Attachment id is required.");
        Objects.requireNonNull(storageKey, "Storage key is required.");
        Objects.requireNonNull(originalName, "Original filename is required.");
        Objects.requireNonNull(mediaType, "Media type is required.");
        Objects.requireNonNull(sha256, "SHA-256 hash is required.");
        Objects.requireNonNull(uploadedByUserId, "Uploader id is required.");
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(createdAt, "Creation time is required.");
    }
}
