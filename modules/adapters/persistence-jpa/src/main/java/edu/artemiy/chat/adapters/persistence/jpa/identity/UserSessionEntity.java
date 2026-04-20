package edu.artemiy.chat.adapters.persistence.jpa.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_sessions")
class UserSessionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", nullable = false, length = 512)
    private String userAgent;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    protected UserSessionEntity() {
    }

    UserSessionEntity(
        UUID id,
        UUID userId,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant revokedAt,
        String userAgent,
        String ipAddress
    ) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getLastSeenAt() {
        return lastSeenAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    String getUserAgent() {
        return userAgent;
    }

    String getIpAddress() {
        return ipAddress;
    }
}
