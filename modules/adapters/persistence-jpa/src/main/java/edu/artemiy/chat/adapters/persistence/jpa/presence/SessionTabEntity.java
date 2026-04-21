package edu.artemiy.chat.adapters.persistence.jpa.presence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "session_tabs")
class SessionTabEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tab_key", nullable = false, length = 128)
    private String tabKey;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "last_ping_at", nullable = false)
    private Instant lastPingAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected SessionTabEntity() {
    }

    SessionTabEntity(
        UUID id,
        UUID sessionId,
        String tabKey,
        Instant connectedAt,
        Instant lastActivityAt,
        Instant lastPingAt,
        Instant closedAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.tabKey = tabKey;
        this.connectedAt = connectedAt;
        this.lastActivityAt = lastActivityAt;
        this.lastPingAt = lastPingAt;
        this.closedAt = closedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getSessionId() {
        return sessionId;
    }

    String getTabKey() {
        return tabKey;
    }

    Instant getConnectedAt() {
        return connectedAt;
    }

    void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    Instant getLastActivityAt() {
        return lastActivityAt;
    }

    void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    Instant getLastPingAt() {
        return lastPingAt;
    }

    void setLastPingAt(Instant lastPingAt) {
        this.lastPingAt = lastPingAt;
    }

    Instant getClosedAt() {
        return closedAt;
    }

    void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
