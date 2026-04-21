package edu.artemiy.chat.rooms.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredRoomBan(
    UUID roomId,
    UUID userId,
    UUID bannedByUserId,
    String reason,
    Instant createdAt
) {
}
