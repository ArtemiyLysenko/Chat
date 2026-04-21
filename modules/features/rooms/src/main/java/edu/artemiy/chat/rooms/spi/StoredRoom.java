package edu.artemiy.chat.rooms.spi;

import java.time.Instant;
import java.util.UUID;

import edu.artemiy.chat.rooms.api.RoomVisibility;

public record StoredRoom(
    UUID id,
    UUID ownerUserId,
    String name,
    String description,
    RoomVisibility visibility,
    Instant createdAt
) {
}
