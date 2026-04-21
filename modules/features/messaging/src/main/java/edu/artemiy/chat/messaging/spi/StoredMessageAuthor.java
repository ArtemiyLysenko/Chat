package edu.artemiy.chat.messaging.spi;

import java.util.Objects;
import java.util.UUID;

public record StoredMessageAuthor(UUID id, String username, String displayName, boolean deleted) {

    public StoredMessageAuthor {
        Objects.requireNonNull(id, "Author id is required.");
        Objects.requireNonNull(username, "Author username is required.");
        Objects.requireNonNull(displayName, "Author display name is required.");
    }
}
