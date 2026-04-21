package edu.artemiy.chat.rooms.spi;

import java.time.Instant;
import java.util.UUID;

import edu.artemiy.chat.rooms.api.MembershipRole;

public record StoredRoomMemberEntry(
    UUID userId,
    MembershipRole role,
    Instant joinedAt
) {
}
