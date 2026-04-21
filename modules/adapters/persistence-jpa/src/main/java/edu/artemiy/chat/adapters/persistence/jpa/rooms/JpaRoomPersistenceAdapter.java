package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.RoomVisibility;
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

@Component
class JpaRoomPersistenceAdapter implements RoomPersistencePort {

    private final RoomJpaRepository roomJpaRepository;
    private final RoomMembershipJpaRepository roomMembershipJpaRepository;
    private final RoomInviteJpaRepository roomInviteJpaRepository;
    private final RoomBanJpaRepository roomBanJpaRepository;
    private final ModerationAuditEventJpaRepository moderationAuditEventJpaRepository;
    private final RoomUserReadRepository roomUserReadRepository;

    JpaRoomPersistenceAdapter(
        RoomJpaRepository roomJpaRepository,
        RoomMembershipJpaRepository roomMembershipJpaRepository,
        RoomInviteJpaRepository roomInviteJpaRepository,
        RoomBanJpaRepository roomBanJpaRepository,
        ModerationAuditEventJpaRepository moderationAuditEventJpaRepository,
        RoomUserReadRepository roomUserReadRepository
    ) {
        this.roomJpaRepository = roomJpaRepository;
        this.roomMembershipJpaRepository = roomMembershipJpaRepository;
        this.roomInviteJpaRepository = roomInviteJpaRepository;
        this.roomBanJpaRepository = roomBanJpaRepository;
        this.moderationAuditEventJpaRepository = moderationAuditEventJpaRepository;
        this.roomUserReadRepository = roomUserReadRepository;
    }

    @Override
    public Optional<StoredRoom> findRoom(UUID roomId) {
        return roomJpaRepository.findById(roomId).map(JpaRoomPersistenceAdapter::toStoredRoom);
    }

    @Override
    public Map<UUID, StoredRoomUser> findUsers(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return roomUserReadRepository.findUsers(List.copyOf(userIds)).stream()
            .map(projection -> new StoredRoomUser(
                projection.getUserId(),
                projection.getUsername(),
                projection.getDisplayName(),
                projection.getDeletedAt() != null
            ))
            .collect(LinkedHashMap::new, (map, user) -> map.put(user.id(), user), Map::putAll);
    }

    @Override
    public Optional<StoredRoomMembership> findMembership(UUID roomId, UUID userId) {
        return roomMembershipJpaRepository.findById(new RoomMembershipEntity.RoomMembershipId(roomId, userId))
            .map(JpaRoomPersistenceAdapter::toStoredMembership);
    }

    @Override
    public Optional<StoredRoomInvite> findInvite(UUID roomId, UUID userId) {
        return roomInviteJpaRepository.findById(new RoomInviteEntity.RoomInviteId(roomId, userId))
            .map(JpaRoomPersistenceAdapter::toStoredInvite);
    }

    @Override
    public Optional<StoredRoomBan> findBan(UUID roomId, UUID userId) {
        return roomBanJpaRepository.findById(new RoomBanEntity.RoomBanId(roomId, userId))
            .map(JpaRoomPersistenceAdapter::toStoredBan);
    }

    @Override
    public List<StoredRoomCatalogItem> listJoinedRooms(UUID userId) {
        return roomJpaRepository.listJoinedRooms(userId).stream().map(JpaRoomPersistenceAdapter::toCatalogItem).toList();
    }

    @Override
    public List<StoredRoomCatalogItem> listCatalogRooms(UUID userId) {
        return roomJpaRepository.listCatalogRooms(userId).stream().map(JpaRoomPersistenceAdapter::toCatalogItem).toList();
    }

    @Override
    public List<StoredRoomMemberEntry> listMembers(UUID roomId) {
        return roomMembershipJpaRepository.findMembers(roomId).stream()
            .map(projection -> new StoredRoomMemberEntry(
                projection.getUserId(),
                MembershipRole.valueOf(projection.getRole()),
                projection.getJoinedAt()
            ))
            .toList();
    }

    @Override
    public List<StoredRoomBanEntry> listBans(UUID roomId) {
        return roomBanJpaRepository.findBanEntries(roomId).stream()
            .map(projection -> new StoredRoomBanEntry(
                projection.getUserId(),
                projection.getBannedByUserId(),
                projection.getReason(),
                projection.getCreatedAt()
            ))
            .toList();
    }

    @Override
    public StoredRoom createRoom(NewRoomRecord room, NewRoomMembershipRecord ownerMembership) {
        try {
            roomJpaRepository.saveAndFlush(new RoomEntity(
                room.id(),
                room.ownerUserId(),
                room.name(),
                room.description(),
                room.visibility(),
                room.createdAt()
            ));
            roomMembershipJpaRepository.saveAndFlush(new RoomMembershipEntity(
                ownerMembership.roomId(),
                ownerMembership.userId(),
                ownerMembership.role(),
                ownerMembership.joinedAt()
            ));
        }
        catch (DataIntegrityViolationException exception) {
            if (isRoomNameConflict(exception)) {
                throw new DuplicateRoomNameException(exception);
            }
            throw exception;
        }
        return new StoredRoom(
            room.id(),
            room.ownerUserId(),
            room.name(),
            room.description(),
            room.visibility(),
            room.createdAt()
        );
    }

    @Override
    public void addMembership(NewRoomMembershipRecord membership) {
        roomMembershipJpaRepository.saveAndFlush(new RoomMembershipEntity(
            membership.roomId(),
            membership.userId(),
            membership.role(),
            membership.joinedAt()
        ));
    }

    @Override
    public void updateMembershipRole(UUID roomId, UUID userId, MembershipRole role) {
        RoomMembershipEntity membership = roomMembershipJpaRepository.findById(new RoomMembershipEntity.RoomMembershipId(roomId, userId))
            .orElseThrow();
        membership.setRole(role);
        roomMembershipJpaRepository.saveAndFlush(membership);
    }

    @Override
    public void removeMembership(UUID roomId, UUID userId) {
        roomMembershipJpaRepository.deleteById(new RoomMembershipEntity.RoomMembershipId(roomId, userId));
    }

    @Override
    public void upsertInvite(NewRoomInviteRecord invite) {
        RoomInviteEntity entity = roomInviteJpaRepository.findById(new RoomInviteEntity.RoomInviteId(invite.roomId(), invite.invitedUserId()))
            .orElseGet(() -> new RoomInviteEntity(
                invite.roomId(),
                invite.invitedUserId(),
                invite.invitedByUserId(),
                RoomInviteStatus.PENDING,
                invite.createdAt(),
                null
            ));
        entity.reopen(invite.invitedByUserId(), invite.createdAt());
        roomInviteJpaRepository.saveAndFlush(entity);
    }

    @Override
    public void markInviteAccepted(UUID roomId, UUID userId, java.time.Instant acceptedAt) {
        roomInviteJpaRepository.findById(new RoomInviteEntity.RoomInviteId(roomId, userId))
            .ifPresent(entity -> {
                entity.markAccepted(acceptedAt);
                roomInviteJpaRepository.saveAndFlush(entity);
            });
    }

    @Override
    public void deleteInvite(UUID roomId, UUID userId) {
        roomInviteJpaRepository.findById(new RoomInviteEntity.RoomInviteId(roomId, userId))
            .ifPresent(roomInviteJpaRepository::delete);
        roomInviteJpaRepository.flush();
    }

    @Override
    public void upsertBan(NewRoomBanRecord ban) {
        RoomBanEntity entity = roomBanJpaRepository.findById(new RoomBanEntity.RoomBanId(ban.roomId(), ban.userId()))
            .orElseGet(() -> new RoomBanEntity(
                ban.roomId(),
                ban.userId(),
                ban.bannedByUserId(),
                ban.reason(),
                ban.createdAt()
            ));
        entity.update(ban.bannedByUserId(), ban.reason(), ban.createdAt());
        roomBanJpaRepository.saveAndFlush(entity);
    }

    @Override
    public void deleteBan(UUID roomId, UUID userId) {
        roomBanJpaRepository.findById(new RoomBanEntity.RoomBanId(roomId, userId))
            .ifPresent(roomBanJpaRepository::delete);
        roomBanJpaRepository.flush();
    }

    @Override
    public void recordModerationEvents(List<NewModerationAuditRecord> records) {
        moderationAuditEventJpaRepository.saveAllAndFlush(records.stream()
            .map(record -> new ModerationAuditEventEntity(
                UUID.randomUUID(),
                record.roomId(),
                record.actorUserId(),
                record.targetUserId(),
                null,
                record.action(),
                record.reason(),
                record.metadataJson(),
                record.createdAt()
            ))
            .toList());
    }

    @Override
    public void deleteRoom(UUID roomId) {
        roomJpaRepository.deleteById(roomId);
        roomJpaRepository.flush();
    }

    private static StoredRoom toStoredRoom(RoomEntity entity) {
        return new StoredRoom(
            entity.getId(),
            entity.getOwnerUserId(),
            entity.getName(),
            entity.getDescription(),
            entity.getVisibility(),
            entity.getCreatedAt()
        );
    }

    private static StoredRoomMembership toStoredMembership(RoomMembershipEntity entity) {
        return new StoredRoomMembership(entity.getRoomId(), entity.getUserId(), entity.getRole(), entity.getJoinedAt());
    }

    private static StoredRoomInvite toStoredInvite(RoomInviteEntity entity) {
        return new StoredRoomInvite(
            entity.getRoomId(),
            entity.getInvitedUserId(),
            entity.getInvitedByUserId(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getAcceptedAt()
        );
    }

    private static StoredRoomBan toStoredBan(RoomBanEntity entity) {
        return new StoredRoomBan(entity.getRoomId(), entity.getUserId(), entity.getBannedByUserId(), entity.getReason(), entity.getCreatedAt());
    }

    private static StoredRoomCatalogItem toCatalogItem(RoomJpaRepository.RoomListProjection projection) {
        return new StoredRoomCatalogItem(
            projection.getRoomId(),
            projection.getName(),
            projection.getDescription(),
            RoomVisibility.valueOf(projection.getVisibility()),
            projection.getOwnerUserId(),
            projection.getViewerRole() == null ? null : MembershipRole.valueOf(projection.getViewerRole()),
            Math.toIntExact(projection.getMemberCount())
        );
    }

    private static boolean isRoomNameConflict(DataIntegrityViolationException exception) {
        String message = Optional.ofNullable(exception.getMostSpecificCause())
            .map(Throwable::getMessage)
            .orElse(exception.getMessage());
        return message != null && message.toLowerCase(Locale.ROOT).contains("uq_rooms_name_lower");
    }
}
