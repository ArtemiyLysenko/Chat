package edu.artemiy.chat.adapters.persistence.jpa.attachments;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageAttachmentJpaRepository extends JpaRepository<MessageAttachmentEntity, UUID> {

    @Query(value = """
        select
            ma.message_id as messageId,
            a.id as attachmentId,
            a.original_name as originalName,
            a.media_type as mediaType,
            a.size_bytes as sizeBytes,
            ma.comment_text as commentText,
            ma.sort_order as sortOrder
        from message_attachments ma
        join attachments a on a.id = ma.attachment_id
        where ma.message_id in (:messageIds)
        order by ma.message_id asc, ma.sort_order asc, a.created_at asc, a.id asc
        """, nativeQuery = true)
    List<MessageAttachmentProjection> findAttachmentProjectionsByMessageIds(@Param("messageIds") Collection<UUID> messageIds);

    interface MessageAttachmentProjection {

        UUID getMessageId();

        UUID getAttachmentId();

        String getOriginalName();

        String getMediaType();

        long getSizeBytes();

        String getCommentText();

        int getSortOrder();
    }
}
