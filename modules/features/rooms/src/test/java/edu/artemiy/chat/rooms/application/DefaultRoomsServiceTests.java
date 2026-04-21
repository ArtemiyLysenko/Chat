package edu.artemiy.chat.rooms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.ModerationAction;
import edu.artemiy.chat.rooms.api.RoomAccessLevel;
import edu.artemiy.chat.rooms.api.RoomScope;
import edu.artemiy.chat.rooms.api.RoomVisibility;
import edu.artemiy.chat.rooms.api.RoomsErrorType;
import edu.artemiy.chat.rooms.api.RoomsException;
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
import edu.artemiy.chat.testing.FixedClock;

class DefaultRoomsServiceTests {

    private static final Instant NOW = Instant.parse("2026-04-21T10:00:00Z");

    private final FakeRoomPersistencePort roomPersistencePort = new FakeRoomPersistencePort();

    private DefaultRoomsService service;

    @BeforeEach
    void setUp() {
        service = new DefaultRoomsService(new FixedClock(NOW), roomPersistencePort);

        roomPersistencePort.addUser(user("owner"));
        roomPersistencePort.addUser(user("moderator"));
        roomPersistencePort.addUser(user("member"));
        roomPersistencePort.addUser(user("invitee"));
        roomPersistencePort.addUser(user("outsider"));
    }

    @Test
    void createRoomCreatesOwnedMembership() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");

        var created = service.createRoom(owner.id(), "Town Square", "Public landing room", RoomVisibility.PUBLIC);

        assertThat(created.name()).isEqualTo("Town Square");
        assertThat(created.viewerRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(roomPersistencePort.findRoom(created.id())).isPresent();
        assertThat(roomPersistencePort.findMembership(created.id(), owner.id()))
            .hasValueSatisfying(membership -> assertThat(membership.role()).isEqualTo(MembershipRole.OWNER));
    }

    @Test
    void publicCatalogVisibilityExcludesPrivateRoomsAndBannedRooms() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser member = roomPersistencePort.userByUsername("member");

        UUID visibleRoomId = roomPersistencePort.addRoom("Lobby", RoomVisibility.PUBLIC, owner.id());
        roomPersistencePort.addRoom("Hidden", RoomVisibility.PRIVATE, owner.id());
        UUID bannedRoomId = roomPersistencePort.addRoom("Moderators", RoomVisibility.PUBLIC, owner.id());
        roomPersistencePort.addBan(bannedRoomId, member.id(), owner.id(), "No access", NOW);

        var catalog = service.listRooms(member.id(), RoomScope.CATALOG);

        assertThat(catalog).extracting(room -> room.id()).containsExactly(visibleRoomId);
    }

    @Test
    void privateRoomHidingFromUnauthorizedUsersReturnsNotFound() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser outsider = roomPersistencePort.userByUsername("outsider");
        UUID roomId = roomPersistencePort.addRoom("Secret", RoomVisibility.PRIVATE, owner.id());

        assertThatThrownBy(() -> service.loadRoomDetails(outsider.id(), roomId))
            .isInstanceOfSatisfying(RoomsException.class, exception -> {
                assertThat(exception.code()).isEqualTo("rooms.room_not_found");
                assertThat(exception.errorType()).isEqualTo(RoomsErrorType.NOT_FOUND);
            });
    }

    @Test
    void privateRoomPreviewForInviteesOnlyExposesOwnerAndName() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser invitee = roomPersistencePort.userByUsername("invitee");
        UUID roomId = roomPersistencePort.addRoom("Backstage", RoomVisibility.PRIVATE, owner.id());
        roomPersistencePort.addInvite(roomId, invitee.id(), owner.id(), NOW);

        var details = service.loadRoomDetails(invitee.id(), roomId);

        assertThat(details.accessLevel()).isEqualTo(RoomAccessLevel.INVITED_PREVIEW);
        assertThat(details.name()).isEqualTo("Backstage");
        assertThat(details.owner().username()).isEqualTo("owner");
        assertThat(details.description()).isNull();
        assertThat(details.visibility()).isNull();
        assertThat(details.members()).isEmpty();
        assertThat(details.canJoin()).isTrue();
    }

    @Test
    void joinWithValidInviteCreatesMembershipAndConsumesInvite() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser invitee = roomPersistencePort.userByUsername("invitee");
        UUID roomId = roomPersistencePort.addRoom("Private Deck", RoomVisibility.PRIVATE, owner.id());
        roomPersistencePort.addInvite(roomId, invitee.id(), owner.id(), NOW.minusSeconds(60));

        service.joinRoom(invitee.id(), roomId);

        assertThat(roomPersistencePort.findMembership(roomId, invitee.id()))
            .hasValueSatisfying(membership -> assertThat(membership.role()).isEqualTo(MembershipRole.MEMBER));
        assertThat(roomPersistencePort.findInvite(roomId, invitee.id()))
            .hasValueSatisfying(invite -> assertThat(invite.status()).isEqualTo(RoomInviteStatus.ACCEPTED));
    }

    @Test
    void joinDenialWhenBannedReturnsForbidden() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser outsider = roomPersistencePort.userByUsername("outsider");
        UUID roomId = roomPersistencePort.addRoom("Lobby", RoomVisibility.PUBLIC, owner.id());
        roomPersistencePort.addBan(roomId, outsider.id(), owner.id(), "Banned", NOW);

        assertThatThrownBy(() -> service.joinRoom(outsider.id(), roomId))
            .isInstanceOfSatisfying(RoomsException.class, exception -> {
                assertThat(exception.code()).isEqualTo("rooms.join_banned");
                assertThat(exception.errorType()).isEqualTo(RoomsErrorType.FORBIDDEN);
            });
    }

    @Test
    void leaveRemovesNonOwnerMembership() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser member = roomPersistencePort.userByUsername("member");
        UUID roomId = roomPersistencePort.addRoom("Lobby", RoomVisibility.PUBLIC, owner.id());
        roomPersistencePort.addMembership(roomId, member.id(), MembershipRole.MEMBER, NOW.minusSeconds(30));

        service.leaveRoom(member.id(), roomId);

        assertThat(roomPersistencePort.findMembership(roomId, member.id())).isEmpty();
    }

    @Test
    void ownerLeaveDenialReturnsBadRequest() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        UUID roomId = roomPersistencePort.addRoom("Owned Room", RoomVisibility.PUBLIC, owner.id());

        assertThatThrownBy(() -> service.leaveRoom(owner.id(), roomId))
            .isInstanceOfSatisfying(RoomsException.class, exception -> {
                assertThat(exception.code()).isEqualTo("rooms.owner_leave_denied");
                assertThat(exception.errorType()).isEqualTo(RoomsErrorType.BAD_REQUEST);
            });
    }

    @Test
    void adminGrantPromotesMemberAndRecordsAudit() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser member = roomPersistencePort.userByUsername("member");
        UUID roomId = roomPersistencePort.addRoom("Control", RoomVisibility.PRIVATE, owner.id());
        roomPersistencePort.addMembership(roomId, member.id(), MembershipRole.MEMBER, NOW.minusSeconds(40));

        service.grantAdmin(owner.id(), roomId, member.id());

        assertThat(roomPersistencePort.findMembership(roomId, member.id()))
            .hasValueSatisfying(membership -> assertThat(membership.role()).isEqualTo(MembershipRole.ADMIN));
        assertThat(roomPersistencePort.recordedAudits)
            .singleElement()
            .extracting(NewModerationAuditRecord::action)
            .isEqualTo(ModerationAction.ADMIN_GRANTED);
    }

    @Test
    void adminRevokeDemotesAdminAndRecordsAudit() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser moderator = roomPersistencePort.userByUsername("moderator");
        UUID roomId = roomPersistencePort.addRoom("Control", RoomVisibility.PRIVATE, owner.id());
        roomPersistencePort.addMembership(roomId, moderator.id(), MembershipRole.ADMIN, NOW.minusSeconds(40));

        service.revokeAdmin(owner.id(), roomId, moderator.id());

        assertThat(roomPersistencePort.findMembership(roomId, moderator.id()))
            .hasValueSatisfying(membership -> assertThat(membership.role()).isEqualTo(MembershipRole.MEMBER));
        assertThat(roomPersistencePort.recordedAudits)
            .singleElement()
            .extracting(NewModerationAuditRecord::action)
            .isEqualTo(ModerationAction.ADMIN_REVOKED);
    }

    @Test
    void regularMemberRemovalDoesNotCreateBan() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser member = roomPersistencePort.userByUsername("member");
        UUID roomId = roomPersistencePort.addRoom("Moderation", RoomVisibility.PUBLIC, owner.id());
        roomPersistencePort.addMembership(roomId, member.id(), MembershipRole.MEMBER, NOW.minusSeconds(10));

        service.removeMember(owner.id(), roomId, member.id());

        assertThat(roomPersistencePort.findMembership(roomId, member.id())).isEmpty();
        assertThat(roomPersistencePort.findBan(roomId, member.id())).isEmpty();
        assertThat(roomPersistencePort.recordedAudits)
            .singleElement()
            .extracting(NewModerationAuditRecord::action)
            .isEqualTo(ModerationAction.MEMBER_REMOVED);
    }

    @Test
    void adminRemovalCreatesBanAndDualAuditEvents() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser moderator = roomPersistencePort.userByUsername("moderator");
        UUID roomId = roomPersistencePort.addRoom("Moderation", RoomVisibility.PUBLIC, owner.id());
        roomPersistencePort.addMembership(roomId, moderator.id(), MembershipRole.ADMIN, NOW.minusSeconds(10));

        service.removeMember(owner.id(), roomId, moderator.id());

        assertThat(roomPersistencePort.findMembership(roomId, moderator.id())).isEmpty();
        assertThat(roomPersistencePort.findBan(roomId, moderator.id())).isPresent();
        assertThat(roomPersistencePort.recordedAudits)
            .extracting(NewModerationAuditRecord::action)
            .containsExactly(ModerationAction.MEMBER_REMOVED, ModerationAction.MEMBER_BANNED);
    }

    @Test
    void banUpsertsBanRemovesMembershipAndDeletesInvite() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser member = roomPersistencePort.userByUsername("member");
        UUID roomId = roomPersistencePort.addRoom("Lobby", RoomVisibility.PRIVATE, owner.id());
        roomPersistencePort.addMembership(roomId, member.id(), MembershipRole.MEMBER, NOW.minusSeconds(15));
        roomPersistencePort.addInvite(roomId, member.id(), owner.id(), NOW.minusSeconds(30));

        service.banUser(owner.id(), roomId, member.id(), "Repeated spam");

        assertThat(roomPersistencePort.findMembership(roomId, member.id())).isEmpty();
        assertThat(roomPersistencePort.findInvite(roomId, member.id())).isEmpty();
        assertThat(roomPersistencePort.findBan(roomId, member.id()))
            .hasValueSatisfying(ban -> assertThat(ban.reason()).isEqualTo("Repeated spam"));
        assertThat(roomPersistencePort.recordedAudits)
            .singleElement()
            .extracting(NewModerationAuditRecord::action)
            .isEqualTo(ModerationAction.MEMBER_BANNED);
    }

    @Test
    void unbanRemovesExistingBanAndRecordsAudit() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser outsider = roomPersistencePort.userByUsername("outsider");
        UUID roomId = roomPersistencePort.addRoom("Lobby", RoomVisibility.PUBLIC, owner.id());
        roomPersistencePort.addBan(roomId, outsider.id(), owner.id(), "Timeout", NOW.minusSeconds(5));

        service.unbanUser(owner.id(), roomId, outsider.id());

        assertThat(roomPersistencePort.findBan(roomId, outsider.id())).isEmpty();
        assertThat(roomPersistencePort.recordedAudits)
            .singleElement()
            .extracting(NewModerationAuditRecord::action)
            .isEqualTo(ModerationAction.MEMBER_UNBANNED);
    }

    @Test
    void deleteRoomCascadesOwnedState() {
        StoredRoomUser owner = roomPersistencePort.userByUsername("owner");
        StoredRoomUser member = roomPersistencePort.userByUsername("member");
        StoredRoomUser invitee = roomPersistencePort.userByUsername("invitee");
        UUID roomId = roomPersistencePort.addRoom("Purge", RoomVisibility.PRIVATE, owner.id());
        roomPersistencePort.addMembership(roomId, member.id(), MembershipRole.MEMBER, NOW.minusSeconds(20));
        roomPersistencePort.addInvite(roomId, invitee.id(), owner.id(), NOW.minusSeconds(10));
        roomPersistencePort.addBan(roomId, member.id(), owner.id(), "Historical", NOW.minusSeconds(5));
        roomPersistencePort.recordedAudits.add(new NewModerationAuditRecord(
            roomId,
            owner.id(),
            member.id(),
            ModerationAction.MEMBER_BANNED,
            "Historical",
            null,
            NOW.minusSeconds(5)
        ));

        service.deleteRoom(owner.id(), roomId);

        assertThat(roomPersistencePort.findRoom(roomId)).isEmpty();
        assertThat(roomPersistencePort.findMembership(roomId, owner.id())).isEmpty();
        assertThat(roomPersistencePort.findMembership(roomId, member.id())).isEmpty();
        assertThat(roomPersistencePort.findInvite(roomId, invitee.id())).isEmpty();
        assertThat(roomPersistencePort.findBan(roomId, member.id())).isEmpty();
        assertThat(roomPersistencePort.recordedAudits).isEmpty();
    }

    private static StoredRoomUser user(String username) {
        return new StoredRoomUser(UUID.randomUUID(), username, capitalize(username), false);
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class FakeRoomPersistencePort implements RoomPersistencePort {

        private final Map<UUID, StoredRoomUser> users = new LinkedHashMap<>();
        private final Map<UUID, StoredRoom> rooms = new LinkedHashMap<>();
        private final Map<String, StoredRoomMembership> memberships = new LinkedHashMap<>();
        private final Map<String, StoredRoomInvite> invites = new LinkedHashMap<>();
        private final Map<String, StoredRoomBan> bans = new LinkedHashMap<>();
        private final List<NewModerationAuditRecord> recordedAudits = new ArrayList<>();

        void addUser(StoredRoomUser user) {
            users.put(user.id(), user);
        }

        StoredRoomUser userByUsername(String username) {
            return users.values().stream().filter(user -> user.username().equals(username)).findFirst().orElseThrow();
        }

        UUID addRoom(String name, RoomVisibility visibility, UUID ownerUserId) {
            UUID roomId = UUID.randomUUID();
            rooms.put(roomId, new StoredRoom(roomId, ownerUserId, name, null, visibility, NOW.minusSeconds(120)));
            addMembership(roomId, ownerUserId, MembershipRole.OWNER, NOW.minusSeconds(120));
            return roomId;
        }

        void addMembership(UUID roomId, UUID userId, MembershipRole role, Instant joinedAt) {
            memberships.put(key(roomId, userId), new StoredRoomMembership(roomId, userId, role, joinedAt));
        }

        void addInvite(UUID roomId, UUID userId, UUID invitedByUserId, Instant createdAt) {
            invites.put(key(roomId, userId), new StoredRoomInvite(
                roomId,
                userId,
                invitedByUserId,
                RoomInviteStatus.PENDING,
                createdAt,
                null
            ));
        }

        void addBan(UUID roomId, UUID userId, UUID bannedByUserId, String reason, Instant createdAt) {
            bans.put(key(roomId, userId), new StoredRoomBan(roomId, userId, bannedByUserId, reason, createdAt));
        }

        @Override
        public Optional<StoredRoom> findRoom(UUID roomId) {
            return Optional.ofNullable(rooms.get(roomId));
        }

        @Override
        public Map<UUID, StoredRoomUser> findUsers(Collection<UUID> userIds) {
            Map<UUID, StoredRoomUser> result = new LinkedHashMap<>();
            for (UUID userId : userIds) {
                StoredRoomUser user = users.get(userId);
                if (user != null) {
                    result.put(userId, user);
                }
            }
            return result;
        }

        @Override
        public Optional<StoredRoomMembership> findMembership(UUID roomId, UUID userId) {
            return Optional.ofNullable(memberships.get(key(roomId, userId)));
        }

        @Override
        public Optional<StoredRoomInvite> findInvite(UUID roomId, UUID userId) {
            return Optional.ofNullable(invites.get(key(roomId, userId)));
        }

        @Override
        public Optional<StoredRoomBan> findBan(UUID roomId, UUID userId) {
            return Optional.ofNullable(bans.get(key(roomId, userId)));
        }

        @Override
        public List<StoredRoomCatalogItem> listJoinedRooms(UUID userId) {
            return memberships.values().stream()
                .filter(membership -> membership.userId().equals(userId))
                .map(membership -> rooms.get(membership.roomId()))
                .filter(room -> room != null)
                .sorted(Comparator.comparing(StoredRoom::name))
                .map(room -> new StoredRoomCatalogItem(
                    room.id(),
                    room.name(),
                    room.description(),
                    room.visibility(),
                    room.ownerUserId(),
                    memberships.get(key(room.id(), userId)).role(),
                    memberCount(room.id())
                ))
                .toList();
        }

        @Override
        public List<StoredRoomCatalogItem> listCatalogRooms(UUID userId) {
            return rooms.values().stream()
                .filter(room -> room.visibility() == RoomVisibility.PUBLIC)
                .filter(room -> !bans.containsKey(key(room.id(), userId)))
                .sorted(Comparator.comparing(StoredRoom::name))
                .map(room -> {
                    StoredRoomMembership membership = memberships.get(key(room.id(), userId));
                    return new StoredRoomCatalogItem(
                        room.id(),
                        room.name(),
                        room.description(),
                        room.visibility(),
                        room.ownerUserId(),
                        membership == null ? null : membership.role(),
                        memberCount(room.id())
                    );
                })
                .toList();
        }

        @Override
        public List<StoredRoomMemberEntry> listMembers(UUID roomId) {
            return memberships.values().stream()
                .filter(membership -> membership.roomId().equals(roomId))
                .sorted(Comparator
                    .comparingInt((StoredRoomMembership membership) -> switch (membership.role()) {
                        case OWNER -> 0;
                        case ADMIN -> 1;
                        case MEMBER -> 2;
                    })
                    .thenComparing(StoredRoomMembership::joinedAt))
                .map(membership -> new StoredRoomMemberEntry(membership.userId(), membership.role(), membership.joinedAt()))
                .toList();
        }

        @Override
        public List<StoredRoomBanEntry> listBans(UUID roomId) {
            return bans.values().stream()
                .filter(ban -> ban.roomId().equals(roomId))
                .sorted(Comparator.comparing(StoredRoomBan::createdAt).reversed())
                .map(ban -> new StoredRoomBanEntry(ban.userId(), ban.bannedByUserId(), ban.reason(), ban.createdAt()))
                .toList();
        }

        @Override
        public StoredRoom createRoom(NewRoomRecord room, NewRoomMembershipRecord ownerMembership) {
            boolean duplicateName = rooms.values().stream().anyMatch(existing -> existing.name().equalsIgnoreCase(room.name()));
            if (duplicateName) {
                throw new DuplicateRoomNameException(null);
            }
            StoredRoom storedRoom = new StoredRoom(
                room.id(),
                room.ownerUserId(),
                room.name(),
                room.description(),
                room.visibility(),
                room.createdAt()
            );
            rooms.put(room.id(), storedRoom);
            addMembership(ownerMembership.roomId(), ownerMembership.userId(), ownerMembership.role(), ownerMembership.joinedAt());
            return storedRoom;
        }

        @Override
        public void addMembership(NewRoomMembershipRecord membership) {
            memberships.put(
                key(membership.roomId(), membership.userId()),
                new StoredRoomMembership(membership.roomId(), membership.userId(), membership.role(), membership.joinedAt())
            );
        }

        @Override
        public void updateMembershipRole(UUID roomId, UUID userId, MembershipRole role) {
            StoredRoomMembership membership = memberships.get(key(roomId, userId));
            memberships.put(key(roomId, userId), new StoredRoomMembership(roomId, userId, role, membership.joinedAt()));
        }

        @Override
        public void removeMembership(UUID roomId, UUID userId) {
            memberships.remove(key(roomId, userId));
        }

        @Override
        public void upsertInvite(NewRoomInviteRecord invite) {
            invites.put(key(invite.roomId(), invite.invitedUserId()), new StoredRoomInvite(
                invite.roomId(),
                invite.invitedUserId(),
                invite.invitedByUserId(),
                RoomInviteStatus.PENDING,
                invite.createdAt(),
                null
            ));
        }

        @Override
        public void markInviteAccepted(UUID roomId, UUID userId, Instant acceptedAt) {
            StoredRoomInvite invite = invites.get(key(roomId, userId));
            invites.put(key(roomId, userId), new StoredRoomInvite(
                roomId,
                userId,
                invite.invitedByUserId(),
                RoomInviteStatus.ACCEPTED,
                invite.createdAt(),
                acceptedAt
            ));
        }

        @Override
        public void deleteInvite(UUID roomId, UUID userId) {
            invites.remove(key(roomId, userId));
        }

        @Override
        public void upsertBan(NewRoomBanRecord ban) {
            bans.put(key(ban.roomId(), ban.userId()), new StoredRoomBan(
                ban.roomId(),
                ban.userId(),
                ban.bannedByUserId(),
                ban.reason(),
                ban.createdAt()
            ));
        }

        @Override
        public void deleteBan(UUID roomId, UUID userId) {
            bans.remove(key(roomId, userId));
        }

        @Override
        public void recordModerationEvents(List<NewModerationAuditRecord> records) {
            recordedAudits.addAll(records);
        }

        @Override
        public void deleteRoom(UUID roomId) {
            rooms.remove(roomId);
            memberships.entrySet().removeIf(entry -> entry.getValue().roomId().equals(roomId));
            invites.entrySet().removeIf(entry -> entry.getValue().roomId().equals(roomId));
            bans.entrySet().removeIf(entry -> entry.getValue().roomId().equals(roomId));
            recordedAudits.removeIf(record -> roomId.equals(record.roomId()));
        }

        private int memberCount(UUID roomId) {
            return (int) memberships.values().stream().filter(membership -> membership.roomId().equals(roomId)).count();
        }

        private static String key(UUID roomId, UUID userId) {
            return roomId + "::" + userId;
        }
    }
}
