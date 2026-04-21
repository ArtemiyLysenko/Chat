package edu.artemiy.chat.rooms.api;

import java.util.Objects;
import java.util.UUID;

public record RoomMessagingAccess(UUID roomId, RoomMessagingAccessStatus status, MembershipRole membershipRole) {

    public RoomMessagingAccess {
        Objects.requireNonNull(roomId, "Room id is required.");
        Objects.requireNonNull(status, "Room messaging access status is required.");
    }
}
