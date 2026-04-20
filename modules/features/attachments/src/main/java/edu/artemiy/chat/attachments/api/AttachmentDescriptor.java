package edu.artemiy.chat.attachments.api;

public record AttachmentDescriptor(
    String id,
    String storageKey,
    String originalName,
    String mediaType,
    long sizeBytes
) {
}
