package edu.artemiy.chat.adapters.persistence.jpa.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserEntity() {
    }

    UserEntity(
        UUID id,
        String email,
        String username,
        String displayName,
        String passwordHash,
        Instant deletedAt,
        Instant createdAt
    ) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getUsername() {
        return username;
    }

    String getDisplayName() {
        return displayName;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    void applyTombstone(String email, String username, String displayName, Instant deletedAt) {
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = null;
        this.deletedAt = deletedAt;
    }
}
