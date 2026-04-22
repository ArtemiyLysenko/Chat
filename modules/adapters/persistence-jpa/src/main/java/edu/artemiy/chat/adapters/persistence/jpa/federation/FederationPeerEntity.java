package edu.artemiy.chat.adapters.persistence.jpa.federation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "federation_peers")
class FederationPeerEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "peer_domain", nullable = false, length = 255)
    private String peerDomain;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "config_json", nullable = false)
    private String configJson;

    protected FederationPeerEntity() {
    }

    FederationPeerEntity(
        UUID id,
        String peerDomain,
        String status,
        Instant lastConnectedAt,
        Instant lastErrorAt,
        String configJson
    ) {
        this.id = id;
        this.peerDomain = peerDomain;
        this.status = status;
        this.lastConnectedAt = lastConnectedAt;
        this.lastErrorAt = lastErrorAt;
        this.configJson = configJson;
    }

    UUID getId() {
        return id;
    }

    String getPeerDomain() {
        return peerDomain;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    Instant getLastConnectedAt() {
        return lastConnectedAt;
    }

    void setLastConnectedAt(Instant lastConnectedAt) {
        this.lastConnectedAt = lastConnectedAt;
    }

    Instant getLastErrorAt() {
        return lastErrorAt;
    }

    void setLastErrorAt(Instant lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }

    String getConfigJson() {
        return configJson;
    }

    void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}
