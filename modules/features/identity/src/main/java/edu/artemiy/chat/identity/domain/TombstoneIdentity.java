package edu.artemiy.chat.identity.domain;

import java.util.UUID;

public record TombstoneIdentity(
    String email,
    String username,
    String displayName
) {

    public static TombstoneIdentity forUser(UUID userId) {
        return new TombstoneIdentity(
            "deleted-%s@tombstone.chat".formatted(userId),
            "deleted-%s".formatted(userId),
            "Deleted user"
        );
    }
}
