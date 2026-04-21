package edu.artemiy.chat.rooms.api;

import java.util.UUID;

public record RoomSummary(
    UUID id,
    String name,
    String description,
    RoomVisibility visibility,
    RoomUserSummary owner,
    MembershipRole viewerRole,
    int memberCount
) {
}
