package edu.artemiy.chat.adapters.persistence.jpa.federation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "xmpp_client_sessions")
class XmppClientSessionEntity {

    @Id
    @Column(nullable = false, length = 128)
    private String id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 255)
    private String jid;

    @Column(length = 128)
    private String resource;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "remote_address", length = 255)
    private String remoteAddress;

    @Column(name = "server_node", nullable = false, length = 128)
    private String serverNode;

    protected XmppClientSessionEntity() {
    }

    XmppClientSessionEntity(
        String id,
        UUID userId,
        String jid,
        String resource,
        String status,
        Instant connectedAt,
        Instant disconnectedAt,
        String remoteAddress,
        String serverNode
    ) {
        this.id = id;
        this.userId = userId;
        this.jid = jid;
        this.resource = resource;
        this.status = status;
        this.connectedAt = connectedAt;
        this.disconnectedAt = disconnectedAt;
        this.remoteAddress = remoteAddress;
        this.serverNode = serverNode;
    }

    String getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    void setUserId(UUID userId) {
        this.userId = userId;
    }

    String getJid() {
        return jid;
    }

    void setJid(String jid) {
        this.jid = jid;
    }

    String getResource() {
        return resource;
    }

    void setResource(String resource) {
        this.resource = resource;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    Instant getConnectedAt() {
        return connectedAt;
    }

    void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    Instant getDisconnectedAt() {
        return disconnectedAt;
    }

    void setDisconnectedAt(Instant disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    String getRemoteAddress() {
        return remoteAddress;
    }

    void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    String getServerNode() {
        return serverNode;
    }

    void setServerNode(String serverNode) {
        this.serverNode = serverNode;
    }
}
