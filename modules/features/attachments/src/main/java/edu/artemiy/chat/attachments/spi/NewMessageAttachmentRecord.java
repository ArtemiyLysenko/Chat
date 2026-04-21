package edu.artemiy.chat.attachments.spi;

import java.util.Objects;
import java.util.UUID;

public record NewMessageAttachmentRecord(
    UUID messageId,
    UUID attachmentId,
    String commentText,
    int sortOrder
) {

    public NewMessageAttachmentRecord {
        Objects.requireNonNull(messageId, "Message id is required.");
        Objects.requireNonNull(attachmentId, "Attachment id is required.");
    }
}
