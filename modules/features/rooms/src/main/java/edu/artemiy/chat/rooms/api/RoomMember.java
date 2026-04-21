package edu.artemiy.chat.rooms.api;

import java.time.Instant;

public record RoomMember(
    RoomUserSummary user,
    MembershipRole role,
    Instant joinedAt,
    boolean canGrantAdmin,
    boolean canRevokeAdmin,
    boolean canRemove
) {
}
