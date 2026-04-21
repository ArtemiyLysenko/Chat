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

    @Column(name = "parent_message_id")
    private UUID parentMessageId;

    @Column(name = "body_text", nullable = false)
    private String bodyText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageState state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected MessageEntity() {
    }

    MessageEntity(
        UUID id,
        UUID roomId,
        UUID directDialogId,
        UUID authorUserId,
        UUID parentMessageId,
        String bodyText,
        MessageState state,
        Instant createdAt,
        Instant editedAt,
        Instant deletedAt
    ) {
        this.id = id;
        this.roomId = roomId;
        this.directDialogId = directDialogId;
        this.authorUserId = authorUserId;
        this.parentMessageId = parentMessageId;
        this.bodyText = bodyText;
        this.state = state;
        this.createdAt = createdAt;
        this.editedAt = editedAt;
        this.deletedAt = deletedAt;
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

    UUID getParentMessageId() {
        return parentMessageId;
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

    Instant getEditedAt() {
        return editedAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }

    void edit(String nextBodyText, Instant nextEditedAt) {
        this.bodyText = nextBodyText;
        this.state = MessageState.EDITED;
        this.editedAt = nextEditedAt;
    }

    void markDeleted(Instant nextDeletedAt) {
        this.state = MessageState.DELETED;
        this.deletedAt = nextDeletedAt;
    }
}
