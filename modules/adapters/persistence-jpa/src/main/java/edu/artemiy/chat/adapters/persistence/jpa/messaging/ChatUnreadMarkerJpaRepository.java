package edu.artemiy.chat.adapters.persistence.jpa.messaging;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChatUnreadMarkerJpaRepository extends JpaRepository<ChatUnreadMarkerEntity, UUID> {

    Optional<ChatUnreadMarkerEntity> findByUserIdAndRoomId(UUID userId, UUID roomId);

    Optional<ChatUnreadMarkerEntity> findByUserIdAndDirectDialogId(UUID userId, UUID directDialogId);
}
