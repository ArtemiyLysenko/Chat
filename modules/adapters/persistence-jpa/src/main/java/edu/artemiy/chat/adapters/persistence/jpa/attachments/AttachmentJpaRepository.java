package edu.artemiy.chat.adapters.persistence.jpa.attachments;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentJpaRepository extends JpaRepository<AttachmentEntity, UUID> {

    @Query("select attachment.storageKey from AttachmentEntity attachment where attachment.roomId = :roomId")
    List<String> findStorageKeysByRoomId(@Param("roomId") UUID roomId);
}
