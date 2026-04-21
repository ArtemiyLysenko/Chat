package edu.artemiy.chat.messaging.spi;

import java.util.Objects;
import java.util.UUID;

public record StoredMessageAttachment(
    UUID attachmentId,
    String originalName,
    String mediaType,
    long sizeBytes,
    String commentText,
    int sortOrder
) {

    public StoredMessageAttachment {
        Objects.requireNonNull(attachmentId, "Attachment id is required.");
        Objects.requireNonNull(originalName, "Original filename is required.");
        Objects.requireNonNull(mediaType, "Media type is required.");
    }
}
