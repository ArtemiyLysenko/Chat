package edu.artemiy.chat.rooms.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import edu.artemiy.chat.rooms.api.MembershipRole;

public interface RoomPersistencePort {

    Optional<StoredRoom> findRoom(UUID roomId);

    Map<UUID, StoredRoomUser> findUsers(Collection<UUID> userIds);

    Optional<StoredRoomMembership> findMembership(UUID roomId, UUID userId);

    Optional<StoredRoomInvite> findInvite(UUID roomId, UUID userId);

    Optional<StoredRoomBan> findBan(UUID roomId, UUID userId);

    List<StoredRoomCatalogItem> listJoinedRooms(UUID userId);

    List<StoredRoomCatalogItem> listCatalogRooms(UUID userId);

    List<StoredRoomMemberEntry> listMembers(UUID roomId);

    List<StoredRoomBanEntry> listBans(UUID roomId);

    StoredRoom createRoom(NewRoomRecord room, NewRoomMembershipRecord ownerMembership);

    void addMembership(NewRoomMembershipRecord membership);

    void updateMembershipRole(UUID roomId, UUID userId, MembershipRole role);

    void removeMembership(UUID roomId, UUID userId);

    void upsertInvite(NewRoomInviteRecord invite);

    void markInviteAccepted(UUID roomId, UUID userId, Instant acceptedAt);

    void deleteInvite(UUID roomId, UUID userId);

    void upsertBan(NewRoomBanRecord ban);

    void deleteBan(UUID roomId, UUID userId);

    void recordModerationEvents(List<NewModerationAuditRecord> records);

    void deleteRoom(UUID roomId);
}
