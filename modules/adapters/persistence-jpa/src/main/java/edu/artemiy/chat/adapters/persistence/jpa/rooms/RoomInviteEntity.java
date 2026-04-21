package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import edu.artemiy.chat.rooms.spi.RoomInviteStatus;

@Entity
@Table(name = "room_invites")
class RoomInviteEntity {

    @EmbeddedId
    private RoomInviteId id;

    @Column(name = "invited_by_user_id", nullable = false)
    private UUID invitedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomInviteStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected RoomInviteEntity() {
    }

    RoomInviteEntity(
        UUID roomId,
        UUID invitedUserId,
        UUID invitedByUserId,
        RoomInviteStatus status,
        Instant createdAt,
        Instant acceptedAt
    ) {
        this.id = new RoomInviteId(roomId, invitedUserId);
        this.invitedByUserId = invitedByUserId;
        this.status = status;
        this.createdAt = createdAt;
        this.acceptedAt = acceptedAt;
    }

    UUID getRoomId() {
        return id.roomId();
    }

    UUID getInvitedUserId() {
        return id.invitedUserId();
    }

    UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    RoomInviteStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getAcceptedAt() {
        return acceptedAt;
    }

    void reopen(UUID invitedByUserId, Instant createdAt) {
        this.invitedByUserId = invitedByUserId;
        this.status = RoomInviteStatus.PENDING;
        this.createdAt = createdAt;
        this.acceptedAt = null;
    }

    void markAccepted(Instant acceptedAt) {
        this.status = RoomInviteStatus.ACCEPTED;
        this.acceptedAt = acceptedAt;
    }

    @Embeddable
    static final class RoomInviteId implements Serializable {

        @Column(name = "room_id", nullable = false)
        private UUID roomId;

        @Column(name = "invited_user_id", nullable = false)
        private UUID invitedUserId;

        protected RoomInviteId() {
        }

        RoomInviteId(UUID roomId, UUID invitedUserId) {
            this.roomId = roomId;
            this.invitedUserId = invitedUserId;
        }

        UUID roomId() {
            return roomId;
        }

        UUID invitedUserId() {
            return invitedUserId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RoomInviteId that)) {
                return false;
            }
            return Objects.equals(roomId, that.roomId) && Objects.equals(invitedUserId, that.invitedUserId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, invitedUserId);
        }
    }
}
