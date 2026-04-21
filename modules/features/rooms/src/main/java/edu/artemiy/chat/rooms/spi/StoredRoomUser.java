package edu.artemiy.chat.rooms.spi;

import java.util.UUID;

public record StoredRoomUser(
    UUID id,
    String username,
    String displayName,
    boolean deleted
) {
}
