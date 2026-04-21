package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "friendships")
class FriendshipEntity {

    @Id
    private UUID id;

    @Column(name = "user_low_id", nullable = false)
    private UUID userLowId;

    @Column(name = "user_high_id", nullable = false)
    private UUID userHighId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FriendshipEntity() {
    }

    FriendshipEntity(UUID id, UUID userLowId, UUID userHighId, Instant createdAt) {
        this.id = id;
        this.userLowId = userLowId;
        this.userHighId = userHighId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserLowId() {
        return userLowId;
    }

    UUID getUserHighId() {
        return userHighId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
