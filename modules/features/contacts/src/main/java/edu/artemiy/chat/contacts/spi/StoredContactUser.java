package edu.artemiy.chat.contacts.spi;

import java.util.UUID;

public record StoredContactUser(
    UUID id,
    String username,
    String displayName,
    boolean deleted
) {
}
