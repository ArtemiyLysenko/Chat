package edu.artemiy.chat.identity.api;

import java.util.UUID;

public record ResolvedUser(
    UUID userId,
    String username,
    String displayName
) {
}
