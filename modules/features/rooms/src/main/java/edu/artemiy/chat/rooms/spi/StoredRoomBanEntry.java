package edu.artemiy.chat.rooms.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredRoomBanEntry(
    UUID userId,
    UUID bannedByUserId,
    String reason,
    Instant createdAt
) {
}
