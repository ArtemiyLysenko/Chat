package edu.artemiy.chat.rooms.api;

import java.util.UUID;

public record RoomUserSummary(
    UUID id,
    String username,
    String displayName
) {
}
