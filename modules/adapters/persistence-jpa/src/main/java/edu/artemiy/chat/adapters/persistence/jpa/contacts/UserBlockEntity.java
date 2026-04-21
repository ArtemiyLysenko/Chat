package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_blocks")
class UserBlockEntity {

    @Id
    private UUID id;

    @Column(name = "blocker_user_id", nullable = false)
    private UUID blockerUserId;

    @Column(name = "blocked_user_id", nullable = false)
    private UUID blockedUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserBlockEntity() {
    }

    UserBlockEntity(UUID id, UUID blockerUserId, UUID blockedUserId, Instant createdAt) {
        this.id = id;
        this.blockerUserId = blockerUserId;
        this.blockedUserId = blockedUserId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getBlockerUserId() {
        return blockerUserId;
    }

    UUID getBlockedUserId() {
        return blockedUserId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
