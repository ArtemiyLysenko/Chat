package edu.artemiy.chat.messaging.api;

import java.util.Objects;
import java.util.UUID;

public record MessageAuthor(UUID id, String username, String displayName, boolean deleted) {

    public MessageAuthor {
        Objects.requireNonNull(id, "Author id is required.");
        Objects.requireNonNull(username, "Author username is required.");
        Objects.requireNonNull(displayName, "Author display name is required.");
    }
}
