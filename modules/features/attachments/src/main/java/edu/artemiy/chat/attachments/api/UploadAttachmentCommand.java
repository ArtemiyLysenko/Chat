package edu.artemiy.chat.attachments.api;

import java.util.Objects;
import java.util.UUID;

import edu.artemiy.chat.messaging.api.ChatTargetRef;

public record UploadAttachmentCommand(
    ChatTargetRef chat,
    String originalName,
    String mediaType,
    byte[] content,
    String commentText,
    UUID messageId
) {

    public UploadAttachmentCommand {
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(content, "Attachment content is required.");
    }

    public long sizeBytes() {
        return content.length;
    }
}
