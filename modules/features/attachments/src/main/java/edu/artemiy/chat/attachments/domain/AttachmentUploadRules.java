package edu.artemiy.chat.attachments.domain;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import edu.artemiy.chat.attachments.api.AttachmentsErrorType;
import edu.artemiy.chat.attachments.api.AttachmentsException;

public final class AttachmentUploadRules {

    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    public static final long MAX_IMAGE_SIZE_BYTES = 3L * 1024L * 1024L;
    public static final int MAX_MESSAGE_BODY_UTF8_BYTES = 3 * 1024;

    private AttachmentUploadRules() {
    }

    public static byte[] requireContent(byte[] content) {
        Objects.requireNonNull(content, "Attachment content is required.");
        if (content.length == 0) {
            throw new AttachmentsException(
                "attachments.file_required",
                "Attachment file content is required.",
                AttachmentsErrorType.BAD_REQUEST
            );
        }
        return content;
    }

    public static void requireValidSize(long sizeBytes, String mediaType) {
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new AttachmentsException(
                "attachments.file_too_large",
                "Attachments must not exceed 20 MB.",
                AttachmentsErrorType.BAD_REQUEST
            );
        }
        if (isImage(mediaType) && sizeBytes > MAX_IMAGE_SIZE_BYTES) {
            throw new AttachmentsException(
                "attachments.image_too_large",
                "Image attachments must not exceed 3 MB.",
                AttachmentsErrorType.BAD_REQUEST
            );
        }
    }

    public static String normalizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "attachment";
        }
        return originalName.strip();
    }

    public static String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return "application/octet-stream";
        }
        return mediaType.strip();
    }

    public static String normalizeComment(String commentText) {
        if (commentText == null || commentText.isBlank()) {
            return null;
        }
        return requireValidMessageBody(commentText.strip());
    }

    public static String defaultMessageBody(String originalName) {
        return requireValidMessageBody(normalizeOriginalName(originalName));
    }

    public static String sha256Hex(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", exception);
        }
    }

    public static boolean isImage(String mediaType) {
        return normalizeMediaType(mediaType).toLowerCase(Locale.ROOT).startsWith("image/");
    }

    public static int utf8Bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String requireValidMessageBody(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            throw new AttachmentsException(
                "attachments.message_body_required",
                "Attachment message text is required.",
                AttachmentsErrorType.BAD_REQUEST
            );
        }
        if (utf8Bytes(bodyText) > MAX_MESSAGE_BODY_UTF8_BYTES) {
            throw new AttachmentsException(
                "attachments.message_body_too_large",
                "Attachment message text must not exceed 3 KB in UTF-8.",
                AttachmentsErrorType.BAD_REQUEST
            );
        }
        return bodyText;
    }
}
