package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import edu.artemiy.chat.rooms.api.RoomVisibility;

@Entity
@Table(name = "rooms")
class RoomEntity {

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomVisibility visibility;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoomEntity() {
    }

    RoomEntity(UUID id, UUID ownerUserId, String name, String description, RoomVisibility visibility, Instant createdAt) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.description = description;
        this.visibility = visibility;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerUserId() {
        return ownerUserId;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    RoomVisibility getVisibility() {
        return visibility;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
