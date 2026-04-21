package edu.artemiy.chat.adapters.persistence.jpa.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    @Query(value = """
        select
            m.id as messageId,
            m.room_id as roomId,
            m.direct_dialog_id as directDialogId,
            m.author_user_id as authorUserId,
            u.username as authorUsername,
            u.display_name as authorDisplayName,
            u.deleted_at as authorDeletedAt,
            m.body_text as bodyText,
            m.state as state,
            m.created_at as createdAt
        from messages m
        join users u on u.id = m.author_user_id
        where m.id = ?1
        """, nativeQuery = true)
    Optional<MessageProjection> findMessageProjectionById(UUID messageId);

    @Query(value = """
        select
            m.id as messageId,
            m.room_id as roomId,
            m.direct_dialog_id as directDialogId,
            m.author_user_id as authorUserId,
            u.username as authorUsername,
            u.display_name as authorDisplayName,
            u.deleted_at as authorDeletedAt,
            m.body_text as bodyText,
            m.state as state,
            m.created_at as createdAt
        from messages m
        join users u on u.id = m.author_user_id
        where m.room_id = ?1
        order by m.created_at desc, m.id desc
        limit ?2
        """, nativeQuery = true)
    List<MessageProjection> findLatestRoomMessages(UUID roomId, int limit);

    @Query(value = """
        select
            m.id as messageId,
            m.room_id as roomId,
            m.direct_dialog_id as directDialogId,
            m.author_user_id as authorUserId,
            u.username as authorUsername,
            u.display_name as authorDisplayName,
            u.deleted_at as authorDeletedAt,
            m.body_text as bodyText,
            m.state as state,
            m.created_at as createdAt
        from messages m
        join users u on u.id = m.author_user_id
        where m.room_id = ?1
          and (m.created_at < ?2 or (m.created_at = ?2 and m.id < ?3))
        order by m.created_at desc, m.id desc
        limit ?4
        """, nativeQuery = true)
    List<MessageProjection> findRoomMessagesBefore(UUID roomId, Instant beforeCreatedAt, UUID beforeMessageId, int limit);

    @Query(value = """
        select
            m.id as messageId,
            m.room_id as roomId,
            m.direct_dialog_id as directDialogId,
            m.author_user_id as authorUserId,
            u.username as authorUsername,
            u.display_name as authorDisplayName,
            u.deleted_at as authorDeletedAt,
            m.body_text as bodyText,
            m.state as state,
            m.created_at as createdAt
        from messages m
        join users u on u.id = m.author_user_id
        where m.direct_dialog_id = ?1
        order by m.created_at desc, m.id desc
        limit ?2
        """, nativeQuery = true)
    List<MessageProjection> findLatestDirectDialogMessages(UUID directDialogId, int limit);

    @Query(value = """
        select
            m.id as messageId,
            m.room_id as roomId,
            m.direct_dialog_id as directDialogId,
            m.author_user_id as authorUserId,
            u.username as authorUsername,
            u.display_name as authorDisplayName,
            u.deleted_at as authorDeletedAt,
            m.body_text as bodyText,
            m.state as state,
            m.created_at as createdAt
        from messages m
        join users u on u.id = m.author_user_id
        where m.direct_dialog_id = ?1
          and (m.created_at < ?2 or (m.created_at = ?2 and m.id < ?3))
        order by m.created_at desc, m.id desc
        limit ?4
        """, nativeQuery = true)
    List<MessageProjection> findDirectDialogMessagesBefore(UUID directDialogId, Instant beforeCreatedAt, UUID beforeMessageId, int limit);

    interface MessageProjection {

        UUID getMessageId();

        UUID getRoomId();

        UUID getDirectDialogId();

        UUID getAuthorUserId();

        String getAuthorUsername();

        String getAuthorDisplayName();

        Instant getAuthorDeletedAt();

        String getBodyText();

        String getState();

        Instant getCreatedAt();
    }
}
