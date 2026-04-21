package edu.artemiy.chat.messaging.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ChatMessage(
    UUID id,
    ChatTargetRef chat,
    MessageAuthor author,
    String bodyText,
    MessageState state,
    Instant createdAt,
    Instant editedAt,
    MessageReplyTarget replyTo,
    List<MessageAttachment> attachments
) {

    public ChatMessage {
        Objects.requireNonNull(id, "Message id is required.");
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(author, "Message author is required.");
        Objects.requireNonNull(bodyText, "Message body is required.");
        Objects.requireNonNull(state, "Message state is required.");
        Objects.requireNonNull(createdAt, "Message creation time is required.");
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
