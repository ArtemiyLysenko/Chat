package edu.artemiy.chat.adapters.persistence.jpa.federation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "federation_traffic_samples")
class FederationTrafficSampleEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "peer_id", nullable = false)
    private FederationPeerEntity peer;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "inbound_messages", nullable = false)
    private long inboundMessages;

    @Column(name = "outbound_messages", nullable = false)
    private long outboundMessages;

    @Column(name = "inbound_stanzas", nullable = false)
    private long inboundStanzas;

    @Column(name = "outbound_stanzas", nullable = false)
    private long outboundStanzas;

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    protected FederationTrafficSampleEntity() {
    }

    FederationTrafficSampleEntity(
        UUID id,
        FederationPeerEntity peer,
        Instant sampledAt,
        long inboundMessages,
        long outboundMessages,
        long inboundStanzas,
        long outboundStanzas,
        long errorCount
    ) {
        this.id = id;
        this.peer = peer;
        this.sampledAt = sampledAt;
        this.inboundMessages = inboundMessages;
        this.outboundMessages = outboundMessages;
        this.inboundStanzas = inboundStanzas;
        this.outboundStanzas = outboundStanzas;
        this.errorCount = errorCount;
    }

    FederationPeerEntity getPeer() {
        return peer;
    }

    Instant getSampledAt() {
        return sampledAt;
    }

    long getInboundMessages() {
        return inboundMessages;
    }

    long getOutboundMessages() {
        return outboundMessages;
    }

    long getInboundStanzas() {
        return inboundStanzas;
    }

    long getOutboundStanzas() {
        return outboundStanzas;
    }

    long getErrorCount() {
        return errorCount;
    }
}
