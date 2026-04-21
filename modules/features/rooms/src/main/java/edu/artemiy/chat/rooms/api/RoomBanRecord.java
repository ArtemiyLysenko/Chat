package edu.artemiy.chat.rooms.api;

import java.time.Instant;

public record RoomBanRecord(
    RoomUserSummary user,
    RoomUserSummary actor,
    String reason,
    Instant createdAt
) {
}
