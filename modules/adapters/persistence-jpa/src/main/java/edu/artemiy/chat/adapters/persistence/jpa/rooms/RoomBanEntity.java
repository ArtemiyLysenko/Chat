package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_bans")
class RoomBanEntity {

    @EmbeddedId
    private RoomBanId id;

    @Column(name = "banned_by_user_id", nullable = false)
    private UUID bannedByUserId;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoomBanEntity() {
    }

    RoomBanEntity(UUID roomId, UUID userId, UUID bannedByUserId, String reason, Instant createdAt) {
        this.id = new RoomBanId(roomId, userId);
        this.bannedByUserId = bannedByUserId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    UUID getRoomId() {
        return id.roomId();
    }

    UUID getUserId() {
        return id.userId();
    }

    UUID getBannedByUserId() {
        return bannedByUserId;
    }

    String getReason() {
        return reason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void update(UUID bannedByUserId, String reason, Instant createdAt) {
        this.bannedByUserId = bannedByUserId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    @Embeddable
    static final class RoomBanId implements Serializable {

        @Column(name = "room_id", nullable = false)
        private UUID roomId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        protected RoomBanId() {
        }

        RoomBanId(UUID roomId, UUID userId) {
            this.roomId = roomId;
            this.userId = userId;
        }

        UUID roomId() {
            return roomId;
        }

        UUID userId() {
            return userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RoomBanId that)) {
                return false;
            }
            return Objects.equals(roomId, that.roomId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, userId);
        }
    }
}
