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

import edu.artemiy.chat.rooms.api.MembershipRole;

@Entity
@Table(name = "room_memberships")
class RoomMembershipEntity {

    @EmbeddedId
    private RoomMembershipId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected RoomMembershipEntity() {
    }

    RoomMembershipEntity(UUID roomId, UUID userId, MembershipRole role, Instant joinedAt) {
        this.id = new RoomMembershipId(roomId, userId);
        this.role = role;
        this.joinedAt = joinedAt;
    }

    UUID getRoomId() {
        return id.roomId();
    }

    UUID getUserId() {
        return id.userId();
    }

    MembershipRole getRole() {
        return role;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }

    void setRole(MembershipRole role) {
        this.role = role;
    }

    @Embeddable
    static final class RoomMembershipId implements Serializable {

        @Column(name = "room_id", nullable = false)
        private UUID roomId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        protected RoomMembershipId() {
        }

        RoomMembershipId(UUID roomId, UUID userId) {
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
            if (!(other instanceof RoomMembershipId that)) {
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
