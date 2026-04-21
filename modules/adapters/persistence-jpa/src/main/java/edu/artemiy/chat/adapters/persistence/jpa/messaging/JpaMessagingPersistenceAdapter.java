package edu.artemiy.chat.adapters.persistence.jpa.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessageState;
import edu.artemiy.chat.messaging.spi.MessagingPersistencePort;
import edu.artemiy.chat.messaging.spi.NewMessageRecord;
import edu.artemiy.chat.messaging.spi.StoredMessage;
import edu.artemiy.chat.messaging.spi.StoredMessageAuthor;
import edu.artemiy.chat.messaging.spi.StoredMessageReplyTarget;
import edu.artemiy.chat.messaging.spi.StoredUnreadMarker;

@Component
class JpaMessagingPersistenceAdapter implements MessagingPersistencePort {

    private static final String UPSERT_ROOM_UNREAD_MARKER_SQL = """
        insert into chat_unread_markers (id, user_id, room_id, last_read_message_id, updated_at)
        values (?, ?, ?, ?, ?)
        on conflict (user_id, room_id) where room_id is not null
        do update
        set last_read_message_id = excluded.last_read_message_id,
            updated_at = excluded.updated_at
        where exists (
            select 1
            from messages candidate
            join messages existing on existing.id = chat_unread_markers.last_read_message_id
            where candidate.id = excluded.last_read_message_id
              and (candidate.created_at, candidate.id) > (existing.created_at, existing.id)
        )
        """;

    private static final String UPSERT_DIRECT_UNREAD_MARKER_SQL = """
        insert into chat_unread_markers (id, user_id, direct_dialog_id, last_read_message_id, updated_at)
        values (?, ?, ?, ?, ?)
        on conflict (user_id, direct_dialog_id) where direct_dialog_id is not null
        do update
        set last_read_message_id = excluded.last_read_message_id,
            updated_at = excluded.updated_at
        where exists (
            select 1
            from messages candidate
            join messages existing on existing.id = chat_unread_markers.last_read_message_id
            where candidate.id = excluded.last_read_message_id
              and (candidate.created_at, candidate.id) > (existing.created_at, existing.id)
        )
        """;

    private static final String COUNT_ROOM_UNREAD_MESSAGES_SQL = """
        select count(*)
        from messages candidate
        left join chat_unread_markers marker
            on marker.user_id = ?
           and marker.room_id = ?
        left join messages last_read
            on last_read.id = marker.last_read_message_id
        where candidate.room_id = ?
          and (
              marker.last_read_message_id is null
              or (candidate.created_at, candidate.id) > (last_read.created_at, last_read.id)
          )
        """;

    private static final String COUNT_DIRECT_UNREAD_MESSAGES_SQL = """
        select count(*)
        from messages candidate
        left join chat_unread_markers marker
            on marker.user_id = ?
           and marker.direct_dialog_id = ?
        left join messages last_read
            on last_read.id = marker.last_read_message_id
        where candidate.direct_dialog_id = ?
          and (
              marker.last_read_message_id is null
              or (candidate.created_at, candidate.id) > (last_read.created_at, last_read.id)
          )
        """;

    private final MessageJpaRepository messageJpaRepository;
    private final ChatUnreadMarkerJpaRepository chatUnreadMarkerJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    JpaMessagingPersistenceAdapter(
        MessageJpaRepository messageJpaRepository,
        ChatUnreadMarkerJpaRepository chatUnreadMarkerJpaRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.messageJpaRepository = messageJpaRepository;
        this.chatUnreadMarkerJpaRepository = chatUnreadMarkerJpaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public StoredMessage createMessage(NewMessageRecord message) {
        messageJpaRepository.saveAndFlush(new MessageEntity(
            message.id(),
            message.chat().type() == ChatTargetType.ROOM ? message.chat().id() : null,
            message.chat().type() == ChatTargetType.DIRECT ? message.chat().id() : null,
            message.authorUserId(),
            message.parentMessageId(),
            message.bodyText(),
            message.state(),
            message.createdAt(),
            null,
            null
        ));
        return findMessage(message.id()).orElseThrow();
    }

    @Override
    public StoredMessage updateMessageBody(UUID messageId, String bodyText, Instant editedAt) {
        MessageEntity entity = messageJpaRepository.findById(messageId).orElseThrow();
        entity.edit(bodyText, editedAt);
        messageJpaRepository.saveAndFlush(entity);
        return findMessage(messageId).orElseThrow();
    }

    @Override
    public StoredMessage markMessageDeleted(UUID messageId, Instant deletedAt) {
        MessageEntity entity = messageJpaRepository.findById(messageId).orElseThrow();
        entity.markDeleted(deletedAt);
        messageJpaRepository.saveAndFlush(entity);
        return findMessage(messageId).orElseThrow();
    }

    @Override
    public Optional<StoredMessage> findMessage(UUID messageId) {
        return messageJpaRepository.findMessageProjectionById(messageId)
            .map(JpaMessagingPersistenceAdapter::toStoredMessage);
    }

    @Override
    public List<StoredMessage> listLatestMessages(ChatTargetRef chat, int limit) {
        return (switch (chat.type()) {
            case ROOM -> messageJpaRepository.findLatestRoomMessages(chat.id(), limit);
            case DIRECT -> messageJpaRepository.findLatestDirectDialogMessages(chat.id(), limit);
        }).stream().map(JpaMessagingPersistenceAdapter::toStoredMessage).toList();
    }

    @Override
    public List<StoredMessage> listMessagesBefore(ChatTargetRef chat, Instant beforeCreatedAt, UUID beforeMessageId, int limit) {
        return (switch (chat.type()) {
            case ROOM -> messageJpaRepository.findRoomMessagesBefore(chat.id(), beforeCreatedAt, beforeMessageId, limit);
            case DIRECT -> messageJpaRepository.findDirectDialogMessagesBefore(chat.id(), beforeCreatedAt, beforeMessageId, limit);
        }).stream().map(JpaMessagingPersistenceAdapter::toStoredMessage).toList();
    }

    @Override
    public Optional<StoredUnreadMarker> findUnreadMarker(UUID userId, ChatTargetRef chat) {
        return findUnreadMarkerEntity(userId, chat).map(JpaMessagingPersistenceAdapter::toStoredUnreadMarker);
    }

    @Override
    public StoredUnreadMarker saveUnreadMarker(UUID userId, ChatTargetRef chat, UUID lastReadMessageId, Instant updatedAt) {
        switch (chat.type()) {
            case ROOM -> jdbcTemplate.update(
                UPSERT_ROOM_UNREAD_MARKER_SQL,
                UUID.randomUUID(),
                userId,
                chat.id(),
                lastReadMessageId,
                Timestamp.from(updatedAt)
            );
            case DIRECT -> jdbcTemplate.update(
                UPSERT_DIRECT_UNREAD_MARKER_SQL,
                UUID.randomUUID(),
                userId,
                chat.id(),
                lastReadMessageId,
                Timestamp.from(updatedAt)
            );
        }
        return findUnreadMarker(userId, chat).orElseThrow();
    }

    @Override
    public int countUnreadMessages(UUID userId, ChatTargetRef chat) {
        Integer unreadCount = switch (chat.type()) {
            case ROOM -> jdbcTemplate.queryForObject(COUNT_ROOM_UNREAD_MESSAGES_SQL, Integer.class, userId, chat.id(), chat.id());
            case DIRECT -> jdbcTemplate.queryForObject(
                COUNT_DIRECT_UNREAD_MESSAGES_SQL,
                Integer.class,
                userId,
                chat.id(),
                chat.id()
            );
        };
        return unreadCount == null ? 0 : unreadCount;
    }

    private Optional<ChatUnreadMarkerEntity> findUnreadMarkerEntity(UUID userId, ChatTargetRef chat) {
        return switch (chat.type()) {
            case ROOM -> chatUnreadMarkerJpaRepository.findByUserIdAndRoomId(userId, chat.id());
            case DIRECT -> chatUnreadMarkerJpaRepository.findByUserIdAndDirectDialogId(userId, chat.id());
        };
    }

    private static StoredMessage toStoredMessage(MessageJpaRepository.MessageProjection projection) {
        return new StoredMessage(
            projection.getMessageId(),
            projection.getRoomId() != null
                ? new ChatTargetRef(ChatTargetType.ROOM, projection.getRoomId())
                : new ChatTargetRef(ChatTargetType.DIRECT, projection.getDirectDialogId()),
            new StoredMessageAuthor(
                projection.getAuthorUserId(),
                projection.getAuthorUsername(),
                projection.getAuthorDisplayName(),
                projection.getAuthorDeletedAt() != null
            ),
            projection.getBodyText(),
            MessageState.valueOf(projection.getState()),
            projection.getCreatedAt(),
            projection.getEditedAt(),
            projection.getParentMessageId() == null
                ? null
                : new StoredMessageReplyTarget(
                    projection.getParentMessageId(),
                    new StoredMessageAuthor(
                        projection.getParentAuthorUserId(),
                        projection.getParentAuthorUsername(),
                        projection.getParentAuthorDisplayName(),
                        projection.getParentAuthorDeletedAt() != null
                    ),
                    projection.getParentBodyText(),
                    MessageState.valueOf(projection.getParentState())
                )
        );
    }

    private static StoredUnreadMarker toStoredUnreadMarker(ChatUnreadMarkerEntity entity) {
        return new StoredUnreadMarker(
            entity.getId(),
            entity.getUserId(),
            entity.getRoomId() != null
                ? new ChatTargetRef(ChatTargetType.ROOM, entity.getRoomId())
                : new ChatTargetRef(ChatTargetType.DIRECT, entity.getDirectDialogId()),
            entity.getLastReadMessageId(),
            entity.getUpdatedAt()
        );
    }
}
