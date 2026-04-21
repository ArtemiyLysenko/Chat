package edu.artemiy.chat.rooms.spi;

import java.time.Instant;
import java.util.UUID;

public record NewRoomInviteRecord(
    UUID roomId,
    UUID invitedUserId,
    UUID invitedByUserId,
    Instant createdAt
) {
}
