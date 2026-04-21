package edu.artemiy.chat.contacts.api;

import java.util.UUID;

public record ContactUserSummary(
    UUID id,
    String username,
    String displayName,
    boolean deleted
) {
}
