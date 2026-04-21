package edu.artemiy.chat.rooms.api;

import java.util.List;
import java.util.UUID;

public record RoomDetails(
    UUID id,
    String name,
    String description,
    RoomVisibility visibility,
    RoomAccessLevel accessLevel,
    RoomUserSummary owner,
    MembershipRole viewerRole,
    int memberCount,
    boolean canJoin,
    boolean canLeave,
    boolean canInvite,
    boolean canManageAdmins,
    boolean canRemoveMembers,
    boolean canInspectBans,
    boolean canManageBans,
    boolean canDelete,
    List<RoomMember> members
) {
}
