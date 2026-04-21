package edu.artemiy.chat.adapters.persistence.jpa.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_unread_markers")
class ChatUnreadMarkerEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "direct_dialog_id")
    private UUID directDialogId;

    @Column(name = "last_read_message_id", nullable = false)
    private UUID lastReadMessageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatUnreadMarkerEntity() {
    }

    ChatUnreadMarkerEntity(
        UUID id,
        UUID userId,
        UUID roomId,
        UUID directDialogId,
        UUID lastReadMessageId,
        Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.roomId = roomId;
        this.directDialogId = directDialogId;
        this.lastReadMessageId = lastReadMessageId;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getRoomId() {
        return roomId;
    }

    UUID getDirectDialogId() {
        return directDialogId;
    }

    UUID getLastReadMessageId() {
        return lastReadMessageId;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void update(UUID lastReadMessageId, Instant updatedAt) {
        this.lastReadMessageId = lastReadMessageId;
        this.updatedAt = updatedAt;
    }
}
