package edu.artemiy.chat.messaging.api;

import java.util.Objects;
import java.util.UUID;

public record MessageReplyTarget(
    UUID messageId,
    MessageAuthor author,
    String bodyText,
    MessageState state
) {

    public MessageReplyTarget {
        Objects.requireNonNull(messageId, "Reply target message id is required.");
        Objects.requireNonNull(author, "Reply target author is required.");
        Objects.requireNonNull(bodyText, "Reply target body is required.");
        Objects.requireNonNull(state, "Reply target state is required.");
    }
}
