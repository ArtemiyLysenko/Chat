package edu.artemiy.chat.adapters.persistence.jpa.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import edu.artemiy.chat.messaging.api.MessageState;

@Entity
@Table(name = "messages")
class MessageEntity {

    @Id
    private UUID id;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "direct_dialog_id")
    private UUID directDialogId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "body_text", nullable = false)
    private String bodyText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageState state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    MessageEntity(
        UUID id,
        UUID roomId,
        UUID directDialogId,
        UUID authorUserId,
        String bodyText,
        MessageState state,
        Instant createdAt
    ) {
        this.id = id;
        this.roomId = roomId;
        this.directDialogId = directDialogId;
        this.authorUserId = authorUserId;
        this.bodyText = bodyText;
        this.state = state;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getRoomId() {
        return roomId;
    }

    UUID getDirectDialogId() {
        return directDialogId;
    }

    UUID getAuthorUserId() {
        return authorUserId;
    }

    String getBodyText() {
        return bodyText;
    }

    MessageState getState() {
        return state;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
