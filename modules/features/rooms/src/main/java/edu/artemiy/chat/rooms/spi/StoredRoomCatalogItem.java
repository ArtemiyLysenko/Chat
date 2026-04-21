package edu.artemiy.chat.rooms.spi;

import java.util.UUID;

import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.RoomVisibility;

public record StoredRoomCatalogItem(
    UUID roomId,
    String name,
    String description,
    RoomVisibility visibility,
    UUID ownerUserId,
    MembershipRole viewerRole,
    int memberCount
) {
}
