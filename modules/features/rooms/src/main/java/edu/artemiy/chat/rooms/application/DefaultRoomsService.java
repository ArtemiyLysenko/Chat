package edu.artemiy.chat.rooms.application;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.ModerationAction;
import edu.artemiy.chat.rooms.api.RoomAccessLevel;
import edu.artemiy.chat.rooms.api.RoomBanRecord;
import edu.artemiy.chat.rooms.api.RoomDetails;
import edu.artemiy.chat.rooms.api.RoomMember;
import edu.artemiy.chat.rooms.api.RoomScope;
import edu.artemiy.chat.rooms.api.RoomSummary;
import edu.artemiy.chat.rooms.api.RoomUserSummary;
import edu.artemiy.chat.rooms.api.RoomVisibility;
import edu.artemiy.chat.rooms.api.RoomsErrorType;
import edu.artemiy.chat.rooms.api.RoomsException;
import edu.artemiy.chat.rooms.api.RoomsService;
import edu.artemiy.chat.rooms.spi.DuplicateRoomNameException;
import edu.artemiy.chat.rooms.spi.NewModerationAuditRecord;
import edu.artemiy.chat.rooms.spi.NewRoomBanRecord;
import edu.artemiy.chat.rooms.spi.NewRoomInviteRecord;
import edu.artemiy.chat.rooms.spi.NewRoomMembershipRecord;
import edu.artemiy.chat.rooms.spi.NewRoomRecord;
import edu.artemiy.chat.rooms.spi.RoomInviteStatus;
import edu.artemiy.chat.rooms.spi.RoomPersistencePort;
import edu.artemiy.chat.rooms.spi.StoredRoom;
import edu.artemiy.chat.rooms.spi.StoredRoomBan;
import edu.artemiy.chat.rooms.spi.StoredRoomBanEntry;
import edu.artemiy.chat.rooms.spi.StoredRoomCatalogItem;
import edu.artemiy.chat.rooms.spi.StoredRoomInvite;
import edu.artemiy.chat.rooms.spi.StoredRoomMemberEntry;
import edu.artemiy.chat.rooms.spi.StoredRoomMembership;
import edu.artemiy.chat.rooms.spi.StoredRoomUser;

@Service
public class DefaultRoomsService implements RoomsService {

    private final ClockPort clockPort;
    private final RoomPersistencePort roomPersistencePort;

    public DefaultRoomsService(ClockPort clockPort, RoomPersistencePort roomPersistencePort) {
        this.clockPort = clockPort;
        this.roomPersistencePort = roomPersistencePort;
    }

    @Override
    @Transactional
    public RoomSummary createRoom(UUID actorUserId, String name, String description, RoomVisibility visibility) {
        StoredRoomUser actor = requireActiveUser(actorUserId);
        String roomName = requireTrimmed(name, "rooms.name_required", "Room name is required.");
        String roomDescription = normalizeOptional(description);
        RoomVisibility roomVisibility = requireVisibility(visibility);
        Instant now = clockPort.now();
        UUID roomId = UUID.randomUUID();

        try {
            roomPersistencePort.createRoom(
                new NewRoomRecord(roomId, actorUserId, roomName, roomDescription, roomVisibility, now),
                new NewRoomMembershipRecord(roomId, actorUserId, MembershipRole.OWNER, now)
            );
        }
        catch (DuplicateRoomNameException exception) {
            throw new RoomsException("rooms.name_taken", "Room name is already in use.", RoomsErrorType.CONFLICT);
        }

        return new RoomSummary(
            roomId,
            roomName,
            roomDescription,
            roomVisibility,
            toUserSummary(actor),
            MembershipRole.OWNER,
            1
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomSummary> listRooms(UUID actorUserId, RoomScope scope) {
        requireActiveUser(actorUserId);
        List<StoredRoomCatalogItem> rooms = switch (scope) {
            case JOINED -> roomPersistencePort.listJoinedRooms(actorUserId);
            case CATALOG -> roomPersistencePort.listCatalogRooms(actorUserId);
        };
        Map<UUID, StoredRoomUser> owners = loadUsers(rooms.stream().map(StoredRoomCatalogItem::ownerUserId).toList());
        return rooms.stream()
            .map(room -> new RoomSummary(
                room.roomId(),
                room.name(),
                room.description(),
                room.visibility(),
                toUserSummary(requireLoadedUser(owners, room.ownerUserId())),
                room.viewerRole(),
                room.memberCount()
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDetails loadRoomDetails(UUID actorUserId, UUID roomId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        if (roomPersistencePort.findBan(roomId, actorUserId).isPresent()) {
            throw hiddenRoom();
        }

        StoredRoomMembership viewerMembership = roomPersistencePort.findMembership(roomId, actorUserId).orElse(null);
        if (room.visibility() == RoomVisibility.PRIVATE && viewerMembership == null) {
            requirePendingInvite(roomId, actorUserId);
            StoredRoomUser owner = requireActiveUser(room.ownerUserId());
            return new RoomDetails(
                room.id(),
                room.name(),
                null,
                null,
                RoomAccessLevel.INVITED_PREVIEW,
                toUserSummary(owner),
                null,
                0,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                List.of()
            );
        }

        List<StoredRoomMemberEntry> memberships = roomPersistencePort.listMembers(roomId);
        Map<UUID, StoredRoomUser> users = loadUsers(userIdsForRoom(room, memberships));
        MembershipRole viewerRole = viewerMembership == null ? null : viewerMembership.role();
        boolean moderator = isModerator(viewerRole);
        boolean ownerViewer = viewerRole == MembershipRole.OWNER;

        return new RoomDetails(
            room.id(),
            room.name(),
            room.description(),
            room.visibility(),
            RoomAccessLevel.FULL,
            toUserSummary(requireLoadedUser(users, room.ownerUserId())),
            viewerRole,
            memberships.size(),
            viewerMembership == null,
            viewerMembership != null && viewerRole != MembershipRole.OWNER,
            moderator && room.visibility() == RoomVisibility.PRIVATE,
            ownerViewer,
            moderator,
            moderator,
            moderator,
            ownerViewer,
            memberships.stream()
                .map(membership -> toRoomMember(users, actorUserId, viewerRole, membership))
                .toList()
        );
    }

    @Override
    @Transactional
    public void inviteUser(UUID actorUserId, UUID roomId, UUID targetUserId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        StoredRoomMembership actorMembership = requireModerator(room, actorUserId);
        if (room.visibility() != RoomVisibility.PRIVATE) {
            throw new RoomsException(
                "rooms.invites_public_room",
                "Invitations are only available for private rooms.",
                RoomsErrorType.BAD_REQUEST
            );
        }

        StoredRoomUser targetUser = requireActiveUser(targetUserId);
        if (room.ownerUserId().equals(targetUserId) || roomPersistencePort.findMembership(roomId, targetUserId).isPresent()) {
            return;
        }
        if (roomPersistencePort.findBan(roomId, targetUserId).isPresent()) {
            throw new RoomsException("rooms.invite_target_banned", "Banned users cannot be invited.", RoomsErrorType.BAD_REQUEST);
        }

        Instant now = clockPort.now();
        roomPersistencePort.upsertInvite(new NewRoomInviteRecord(roomId, targetUser.id(), actorMembership.userId(), now));
    }

    @Override
    @Transactional
    public void joinRoom(UUID actorUserId, UUID roomId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        if (roomPersistencePort.findBan(roomId, actorUserId).isPresent()) {
            throw new RoomsException("rooms.join_banned", "You are banned from this room.", RoomsErrorType.FORBIDDEN);
        }
        if (roomPersistencePort.findMembership(roomId, actorUserId).isPresent()) {
            return;
        }

        Instant now = clockPort.now();
        if (room.visibility() == RoomVisibility.PRIVATE) {
            requirePendingInvite(roomId, actorUserId);
            roomPersistencePort.addMembership(new NewRoomMembershipRecord(roomId, actorUserId, MembershipRole.MEMBER, now));
            roomPersistencePort.markInviteAccepted(roomId, actorUserId, now);
            return;
        }

        roomPersistencePort.addMembership(new NewRoomMembershipRecord(roomId, actorUserId, MembershipRole.MEMBER, now));
    }

    @Override
    @Transactional
    public void leaveRoom(UUID actorUserId, UUID roomId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        StoredRoomMembership membership = requireMembership(room, actorUserId);
        if (membership.role() == MembershipRole.OWNER) {
            throw new RoomsException(
                "rooms.owner_leave_denied",
                "Room owners cannot leave their room. Delete the room instead.",
                RoomsErrorType.BAD_REQUEST
            );
        }
        roomPersistencePort.removeMembership(roomId, actorUserId);
    }

    @Override
    @Transactional
    public void deleteRoom(UUID actorUserId, UUID roomId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        StoredRoomMembership membership = requireMembership(room, actorUserId);
        if (membership.role() != MembershipRole.OWNER) {
            throw new RoomsException("rooms.delete_forbidden", "Only the room owner can delete the room.", RoomsErrorType.FORBIDDEN);
        }

        Instant now = clockPort.now();
        roomPersistencePort.recordModerationEvents(List.of(new NewModerationAuditRecord(
            roomId,
            actorUserId,
            null,
            ModerationAction.ROOM_DELETED,
            null,
            metadataJson(Map.of("roomName", room.name(), "visibility", room.visibility().name())),
            now
        )));
        roomPersistencePort.deleteRoom(roomId);
    }

    @Override
    @Transactional
    public void grantAdmin(UUID actorUserId, UUID roomId, UUID targetUserId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        requireOwner(room, actorUserId);
        StoredRoomMembership targetMembership = requireTargetMembership(room, targetUserId);
        if (targetMembership.role() == MembershipRole.OWNER) {
            throw new RoomsException("rooms.owner_role_fixed", "The room owner role cannot be changed.", RoomsErrorType.BAD_REQUEST);
        }
        if (targetMembership.role() == MembershipRole.ADMIN) {
            return;
        }

        Instant now = clockPort.now();
        roomPersistencePort.updateMembershipRole(roomId, targetUserId, MembershipRole.ADMIN);
        roomPersistencePort.recordModerationEvents(List.of(new NewModerationAuditRecord(
            roomId,
            actorUserId,
            targetUserId,
            ModerationAction.ADMIN_GRANTED,
            null,
            metadataJson(Map.of("role", MembershipRole.ADMIN.name())),
            now
        )));
    }

    @Override
    @Transactional
    public void revokeAdmin(UUID actorUserId, UUID roomId, UUID targetUserId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        requireOwner(room, actorUserId);
        StoredRoomMembership targetMembership = requireTargetMembership(room, targetUserId);
        if (targetMembership.role() == MembershipRole.OWNER) {
            throw new RoomsException("rooms.owner_role_fixed", "The room owner role cannot be changed.", RoomsErrorType.BAD_REQUEST);
        }
        if (targetMembership.role() == MembershipRole.MEMBER) {
            return;
        }

        Instant now = clockPort.now();
        roomPersistencePort.updateMembershipRole(roomId, targetUserId, MembershipRole.MEMBER);
        roomPersistencePort.recordModerationEvents(List.of(new NewModerationAuditRecord(
            roomId,
            actorUserId,
            targetUserId,
            ModerationAction.ADMIN_REVOKED,
            null,
            metadataJson(Map.of("role", MembershipRole.MEMBER.name())),
            now
        )));
    }

    @Override
    @Transactional
    public void removeMember(UUID actorUserId, UUID roomId, UUID targetUserId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        StoredRoomMembership actorMembership = requireModerator(room, actorUserId);
        StoredRoomMembership targetMembership = requireTargetMembership(room, targetUserId);
        if (targetMembership.role() == MembershipRole.OWNER) {
            throw new RoomsException("rooms.owner_role_fixed", "The room owner cannot be removed.", RoomsErrorType.BAD_REQUEST);
        }
        if (!canRemove(actorMembership.role(), actorUserId, targetUserId, targetMembership.role())) {
            throw new RoomsException("rooms.remove_forbidden", "You cannot remove that member.", RoomsErrorType.FORBIDDEN);
        }

        Instant now = clockPort.now();
        roomPersistencePort.removeMembership(roomId, targetUserId);
        if (targetMembership.role() == MembershipRole.ADMIN) {
            roomPersistencePort.upsertBan(new NewRoomBanRecord(roomId, targetUserId, actorUserId, null, now));
            roomPersistencePort.deleteInvite(roomId, targetUserId);
            roomPersistencePort.recordModerationEvents(List.of(
                new NewModerationAuditRecord(
                    roomId,
                    actorUserId,
                    targetUserId,
                    ModerationAction.MEMBER_REMOVED,
                    null,
                    metadataJson(Map.of("previousRole", MembershipRole.ADMIN.name())),
                    now
                ),
                new NewModerationAuditRecord(
                    roomId,
                    actorUserId,
                    targetUserId,
                    ModerationAction.MEMBER_BANNED,
                    null,
                    metadataJson(Map.of("origin", "member-removal", "previousRole", MembershipRole.ADMIN.name())),
                    now
                )
            ));
            return;
        }

        roomPersistencePort.recordModerationEvents(List.of(new NewModerationAuditRecord(
            roomId,
            actorUserId,
            targetUserId,
            ModerationAction.MEMBER_REMOVED,
            null,
            metadataJson(Map.of("previousRole", MembershipRole.MEMBER.name())),
            now
        )));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomBanRecord> listBans(UUID actorUserId, UUID roomId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        requireModerator(room, actorUserId);
        List<StoredRoomBanEntry> bans = roomPersistencePort.listBans(roomId);
        Map<UUID, StoredRoomUser> users = loadUsers(userIdsForBans(bans));
        return bans.stream()
            .map(ban -> new RoomBanRecord(
                toUserSummary(requireLoadedUser(users, ban.userId())),
                toUserSummary(requireLoadedUser(users, ban.bannedByUserId())),
                ban.reason(),
                ban.createdAt()
            ))
            .toList();
    }

    @Override
    @Transactional
    public void banUser(UUID actorUserId, UUID roomId, UUID targetUserId, String reason) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        StoredRoomMembership actorMembership = requireModerator(room, actorUserId);
        requireActiveUser(targetUserId);
        if (targetUserId.equals(actorUserId)) {
            throw new RoomsException("rooms.self_ban_denied", "You cannot ban yourself.", RoomsErrorType.BAD_REQUEST);
        }
        if (room.ownerUserId().equals(targetUserId)) {
            throw new RoomsException("rooms.owner_ban_denied", "The room owner cannot be banned.", RoomsErrorType.BAD_REQUEST);
        }

        StoredRoomMembership targetMembership = roomPersistencePort.findMembership(roomId, targetUserId).orElse(null);
        if (targetMembership != null && !canBan(actorMembership.role(), targetMembership.role())) {
            throw new RoomsException("rooms.ban_forbidden", "You cannot ban that member.", RoomsErrorType.FORBIDDEN);
        }

        Instant now = clockPort.now();
        String normalizedReason = normalizeOptional(reason);
        roomPersistencePort.upsertBan(new NewRoomBanRecord(roomId, targetUserId, actorUserId, normalizedReason, now));
        roomPersistencePort.deleteInvite(roomId, targetUserId);
        if (targetMembership != null) {
            roomPersistencePort.removeMembership(roomId, targetUserId);
        }
        roomPersistencePort.recordModerationEvents(List.of(new NewModerationAuditRecord(
            roomId,
            actorUserId,
            targetUserId,
            ModerationAction.MEMBER_BANNED,
            normalizedReason,
            targetMembership == null
                ? null
                : metadataJson(Map.of("previousRole", targetMembership.role().name(), "membershipRemoved", "true")),
            now
        )));
    }

    @Override
    @Transactional
    public void unbanUser(UUID actorUserId, UUID roomId, UUID targetUserId) {
        requireActiveUser(actorUserId);
        StoredRoom room = requireRoom(roomId);
        requireModerator(room, actorUserId);
        StoredRoomBan existingBan = roomPersistencePort.findBan(roomId, targetUserId).orElse(null);
        if (existingBan == null) {
            return;
        }

        Instant now = clockPort.now();
        roomPersistencePort.deleteBan(roomId, targetUserId);
        roomPersistencePort.recordModerationEvents(List.of(new NewModerationAuditRecord(
            roomId,
            actorUserId,
            targetUserId,
            ModerationAction.MEMBER_UNBANNED,
            null,
            null,
            now
        )));
    }

    private StoredRoom requireRoom(UUID roomId) {
        return roomPersistencePort.findRoom(roomId)
            .orElseThrow(DefaultRoomsService::hiddenRoom);
    }

    private StoredRoomUser requireActiveUser(UUID userId) {
        StoredRoomUser user = roomPersistencePort.findUsers(List.of(userId)).get(userId);
        if (user == null || user.deleted()) {
            throw new RoomsException("rooms.user_not_found", "User was not found.", RoomsErrorType.NOT_FOUND);
        }
        return user;
    }

    private StoredRoomMembership requireMembership(StoredRoom room, UUID actorUserId) {
        return roomPersistencePort.findMembership(room.id(), actorUserId)
            .orElseThrow(() -> membershipRequired(room));
    }

    private StoredRoomMembership requireModerator(StoredRoom room, UUID actorUserId) {
        StoredRoomMembership membership = requireMembership(room, actorUserId);
        if (!isModerator(membership.role())) {
            throw new RoomsException("rooms.moderation_forbidden", "Only room admins or owners can do that.", RoomsErrorType.FORBIDDEN);
        }
        return membership;
    }

    private void requireOwner(StoredRoom room, UUID actorUserId) {
        StoredRoomMembership membership = requireMembership(room, actorUserId);
        if (membership.role() != MembershipRole.OWNER) {
            throw new RoomsException("rooms.owner_required", "Only the room owner can do that.", RoomsErrorType.FORBIDDEN);
        }
    }

    private StoredRoomMembership requireTargetMembership(StoredRoom room, UUID targetUserId) {
        return roomPersistencePort.findMembership(room.id(), targetUserId)
            .orElseThrow(() -> new RoomsException("rooms.member_not_found", "Room member was not found.", RoomsErrorType.NOT_FOUND));
    }

    private StoredRoomInvite requirePendingInvite(UUID roomId, UUID actorUserId) {
        return roomPersistencePort.findInvite(roomId, actorUserId)
            .filter(invite -> invite.status() == RoomInviteStatus.PENDING)
            .orElseThrow(DefaultRoomsService::hiddenRoom);
    }

    private Map<UUID, StoredRoomUser> loadUsers(Collection<UUID> userIds) {
        return userIds.isEmpty() ? Map.of() : roomPersistencePort.findUsers(userIds);
    }

    private RoomMember toRoomMember(
        Map<UUID, StoredRoomUser> users,
        UUID actorUserId,
        MembershipRole viewerRole,
        StoredRoomMemberEntry membership
    ) {
        return new RoomMember(
            toUserSummary(requireLoadedUser(users, membership.userId())),
            membership.role(),
            membership.joinedAt(),
            viewerRole == MembershipRole.OWNER && membership.role() == MembershipRole.MEMBER && !actorUserId.equals(membership.userId()),
            viewerRole == MembershipRole.OWNER && membership.role() == MembershipRole.ADMIN && !actorUserId.equals(membership.userId()),
            canRemove(viewerRole, actorUserId, membership.userId(), membership.role())
        );
    }

    private static Collection<UUID> userIdsForRoom(StoredRoom room, List<StoredRoomMemberEntry> memberships) {
        Map<UUID, UUID> ids = new LinkedHashMap<>();
        ids.put(room.ownerUserId(), room.ownerUserId());
        for (StoredRoomMemberEntry membership : memberships) {
            ids.put(membership.userId(), membership.userId());
        }
        return ids.keySet();
    }

    private static Collection<UUID> userIdsForBans(List<StoredRoomBanEntry> bans) {
        Map<UUID, UUID> ids = new LinkedHashMap<>();
        for (StoredRoomBanEntry ban : bans) {
            ids.put(ban.userId(), ban.userId());
            ids.put(ban.bannedByUserId(), ban.bannedByUserId());
        }
        return ids.keySet();
    }

    private static RoomsException membershipRequired(StoredRoom room) {
        return room.visibility() == RoomVisibility.PRIVATE
            ? hiddenRoom()
            : new RoomsException("rooms.membership_required", "Join the room first.", RoomsErrorType.FORBIDDEN);
    }

    private static RoomVisibility requireVisibility(RoomVisibility visibility) {
        if (visibility == null) {
            throw new RoomsException("rooms.visibility_required", "Room visibility is required.", RoomsErrorType.BAD_REQUEST);
        }
        return visibility;
    }

    private static RoomsException hiddenRoom() {
        return new RoomsException("rooms.room_not_found", "Room was not found.", RoomsErrorType.NOT_FOUND);
    }

    private static StoredRoomUser requireLoadedUser(Map<UUID, StoredRoomUser> users, UUID userId) {
        StoredRoomUser user = users.get(userId);
        if (user == null) {
            throw new RoomsException("rooms.user_not_found", "User was not found.", RoomsErrorType.NOT_FOUND);
        }
        return user;
    }

    private static RoomUserSummary toUserSummary(StoredRoomUser user) {
        return new RoomUserSummary(user.id(), user.username(), user.displayName());
    }

    private static boolean isModerator(MembershipRole role) {
        return role == MembershipRole.OWNER || role == MembershipRole.ADMIN;
    }

    private static boolean canRemove(MembershipRole actorRole, UUID actorUserId, UUID targetUserId, MembershipRole targetRole) {
        if (actorRole == null || actorUserId.equals(targetUserId) || targetRole == MembershipRole.OWNER) {
            return false;
        }
        return switch (actorRole) {
            case OWNER -> true;
            case ADMIN -> targetRole == MembershipRole.MEMBER;
            case MEMBER -> false;
        };
    }

    private static boolean canBan(MembershipRole actorRole, MembershipRole targetRole) {
        return switch (actorRole) {
            case OWNER -> targetRole != MembershipRole.OWNER;
            case ADMIN -> targetRole == MembershipRole.MEMBER;
            case MEMBER -> false;
        };
    }

    private static String requireTrimmed(String value, String code, String message) {
        String trimmed = normalizeOptional(value);
        if (trimmed == null) {
            throw new RoomsException(code, message, RoomsErrorType.BAD_REQUEST);
        }
        return trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String metadataJson(Map<String, String> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.entrySet().stream()
            .map(entry -> "\"%s\":\"%s\"".formatted(escapeJson(entry.getKey()), escapeJson(entry.getValue())))
            .reduce((left, right) -> left + "," + right)
            .map(body -> "{" + body + "}")
            .orElse(null);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
