package edu.artemiy.chat.rooms.spi;

import java.time.Instant;
import java.util.UUID;

public record StoredRoomInvite(
    UUID roomId,
    UUID invitedUserId,
    UUID invitedByUserId,
    RoomInviteStatus status,
    Instant createdAt,
    Instant acceptedAt
) {
}
