package edu.artemiy.chat.attachments.api;

import java.util.Objects;

public record AttachmentDownload(
    AttachmentDescriptor descriptor,
    byte[] content
) {

    public AttachmentDownload {
        Objects.requireNonNull(descriptor, "Attachment descriptor is required.");
        Objects.requireNonNull(content, "Attachment content is required.");
    }
}
