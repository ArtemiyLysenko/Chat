package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import edu.artemiy.chat.contacts.spi.FriendshipRequestStatus;

@Entity
@Table(name = "friendship_requests")
class FriendshipRequestEntity {

    @Id
    private UUID id;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "message_text")
    private String messageText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FriendshipRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected FriendshipRequestEntity() {
    }

    FriendshipRequestEntity(
        UUID id,
        UUID requesterUserId,
        UUID recipientUserId,
        String messageText,
        FriendshipRequestStatus status,
        Instant createdAt,
        Instant respondedAt
    ) {
        this.id = id;
        this.requesterUserId = requesterUserId;
        this.recipientUserId = recipientUserId;
        this.messageText = messageText;
        this.status = status;
        this.createdAt = createdAt;
        this.respondedAt = respondedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getRequesterUserId() {
        return requesterUserId;
    }

    UUID getRecipientUserId() {
        return recipientUserId;
    }

    String getMessageText() {
        return messageText;
    }

    FriendshipRequestStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getRespondedAt() {
        return respondedAt;
    }

    void markAccepted(Instant respondedAt) {
        this.status = FriendshipRequestStatus.ACCEPTED;
        this.respondedAt = respondedAt;
    }

    void markRejected(Instant respondedAt) {
        this.status = FriendshipRequestStatus.REJECTED;
        this.respondedAt = respondedAt;
    }
}
