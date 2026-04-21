package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RoomInviteJpaRepository extends JpaRepository<RoomInviteEntity, RoomInviteEntity.RoomInviteId> {

    Optional<RoomInviteEntity> findById(RoomInviteEntity.RoomInviteId id);

    void deleteByIdInvitedUserId(UUID invitedUserId);

    void deleteByInvitedByUserId(UUID invitedByUserId);
}
