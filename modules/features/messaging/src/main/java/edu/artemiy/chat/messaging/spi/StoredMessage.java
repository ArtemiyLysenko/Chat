package edu.artemiy.chat.messaging.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.MessageState;

public record StoredMessage(
    UUID id,
    ChatTargetRef chat,
    StoredMessageAuthor author,
    String bodyText,
    MessageState state,
    Instant createdAt,
    Instant editedAt,
    StoredMessageReplyTarget replyTo
) {

    public StoredMessage {
        Objects.requireNonNull(id, "Message id is required.");
        Objects.requireNonNull(chat, "Chat target is required.");
        Objects.requireNonNull(author, "Message author is required.");
        Objects.requireNonNull(bodyText, "Message body is required.");
        Objects.requireNonNull(state, "Message state is required.");
        Objects.requireNonNull(createdAt, "Message creation time is required.");
    }
}
