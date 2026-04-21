package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import edu.artemiy.chat.rooms.api.ModerationAction;

@Entity
@Table(name = "moderation_audit_events")
class ModerationAuditEventEntity {

    @Id
    private UUID id;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "message_id")
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModerationAction action;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModerationAuditEventEntity() {
    }

    ModerationAuditEventEntity(
        UUID id,
        UUID roomId,
        UUID actorUserId,
        UUID targetUserId,
        UUID messageId,
        ModerationAction action,
        String reason,
        String metadataJson,
        Instant createdAt
    ) {
        this.id = id;
        this.roomId = roomId;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.messageId = messageId;
        this.action = action;
        this.reason = reason;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getRoomId() {
        return roomId;
    }

    UUID getActorUserId() {
        return actorUserId;
    }

    UUID getTargetUserId() {
        return targetUserId;
    }

    ModerationAction getAction() {
        return action;
    }

    String getReason() {
        return reason;
    }

    String getMetadataJson() {
        return metadataJson;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
