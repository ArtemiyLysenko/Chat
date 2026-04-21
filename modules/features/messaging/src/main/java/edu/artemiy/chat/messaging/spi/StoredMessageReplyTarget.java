package edu.artemiy.chat.messaging.spi;

import java.util.Objects;
import java.util.UUID;

import edu.artemiy.chat.messaging.api.MessageState;

public record StoredMessageReplyTarget(
    UUID messageId,
    StoredMessageAuthor author,
    String bodyText,
    MessageState state
) {

    public StoredMessageReplyTarget {
        Objects.requireNonNull(messageId, "Reply target message id is required.");
        Objects.requireNonNull(author, "Reply target author is required.");
        Objects.requireNonNull(bodyText, "Reply target body is required.");
        Objects.requireNonNull(state, "Reply target state is required.");
    }
}
