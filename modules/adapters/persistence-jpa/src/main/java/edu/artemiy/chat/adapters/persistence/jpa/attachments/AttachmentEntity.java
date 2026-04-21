package edu.artemiy.chat.adapters.persistence.jpa.attachments;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import edu.artemiy.chat.messaging.api.ChatTargetType;

@Entity
@Table(name = "attachments")
class AttachmentEntity {

    @Id
    private UUID id;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_target_type", nullable = false, length = 16)
    private ChatTargetType chatTargetType;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "direct_dialog_id")
    private UUID directDialogId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AttachmentEntity() {
    }

    AttachmentEntity(
        UUID id,
        String storageKey,
        String originalName,
        String mediaType,
        long sizeBytes,
        String sha256,
        UUID uploadedByUserId,
        ChatTargetType chatTargetType,
        UUID roomId,
        UUID directDialogId,
        Instant createdAt
    ) {
        this.id = id;
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedByUserId = uploadedByUserId;
        this.chatTargetType = chatTargetType;
        this.roomId = roomId;
        this.directDialogId = directDialogId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    String getStorageKey() {
        return storageKey;
    }

    String getOriginalName() {
        return originalName;
    }

    String getMediaType() {
        return mediaType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getSha256() {
        return sha256;
    }

    UUID getUploadedByUserId() {
        return uploadedByUserId;
    }

    ChatTargetType getChatTargetType() {
        return chatTargetType;
    }

    UUID getRoomId() {
        return roomId;
    }

    UUID getDirectDialogId() {
        return directDialogId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
