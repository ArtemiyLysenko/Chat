package edu.artemiy.chat.adapters.persistence.jpa.federation;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationPeerStatus;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionStatus;
import edu.artemiy.chat.federation.spi.FederationPersistencePort;
import edu.artemiy.chat.federation.spi.XmppClientSessionRecord;

@Component
class JpaFederationPersistenceAdapter implements FederationPersistencePort {

    private final XmppClientSessionJpaRepository xmppClientSessionJpaRepository;
    private final FederationPeerJpaRepository federationPeerJpaRepository;
    private final FederationTrafficSampleJpaRepository federationTrafficSampleJpaRepository;

    JpaFederationPersistenceAdapter(
        XmppClientSessionJpaRepository xmppClientSessionJpaRepository,
        FederationPeerJpaRepository federationPeerJpaRepository,
        FederationTrafficSampleJpaRepository federationTrafficSampleJpaRepository
    ) {
        this.xmppClientSessionJpaRepository = xmppClientSessionJpaRepository;
        this.federationPeerJpaRepository = federationPeerJpaRepository;
        this.federationTrafficSampleJpaRepository = federationTrafficSampleJpaRepository;
    }

    @Override
    @Transactional
    public void upsertXmppClientSession(XmppClientSessionRecord record) {
        XmppClientSessionEntity entity = xmppClientSessionJpaRepository.findById(record.sessionId())
            .orElseGet(() -> new XmppClientSessionEntity(
                record.sessionId(),
                record.userId(),
                record.jid(),
                record.resource(),
                record.status().name(),
                requiredConnectedAt(record),
                record.disconnectedAt(),
                normalizedText(record.remoteAddress()),
                requiredText(record.serverNode(), "serverNode")
            ));
        if (record.userId() != null) {
            entity.setUserId(record.userId());
        }
        if (record.jid() != null) {
            entity.setJid(record.jid());
        }
        if (record.resource() != null) {
            entity.setResource(record.resource());
        }
        entity.setStatus(record.status().name());
        if (record.connectedAt() != null && entity.getConnectedAt() == null) {
            entity.setConnectedAt(record.connectedAt());
        }
        if (record.disconnectedAt() != null) {
            entity.setDisconnectedAt(record.disconnectedAt());
        }
        if (record.remoteAddress() != null) {
            entity.setRemoteAddress(record.remoteAddress());
        }
        if (record.serverNode() != null) {
            entity.setServerNode(record.serverNode());
        }
        xmppClientSessionJpaRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void upsertFederationPeer(
        String peerDomain,
        FederationPeerStatus status,
        Instant lastConnectedAt,
        Instant lastErrorAt,
        String configJson
    ) {
        FederationPeerEntity entity = getOrCreatePeer(peerDomain, configJson);
        entity.setStatus(status.name());
        if (lastConnectedAt != null) {
            entity.setLastConnectedAt(latest(entity.getLastConnectedAt(), lastConnectedAt));
        }
        if (lastErrorAt != null) {
            entity.setLastErrorAt(latest(entity.getLastErrorAt(), lastErrorAt));
        }
        if (configJson != null && !configJson.isBlank()) {
            entity.setConfigJson(configJson);
        }
        federationPeerJpaRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void appendFederationTrafficSample(
        String peerDomain,
        String configJson,
        long inboundMessagesDelta,
        long outboundMessagesDelta,
        long inboundStanzasDelta,
        long outboundStanzasDelta,
        long errorDelta,
        Instant sampledAt
    ) {
        FederationPeerEntity peer = getOrCreatePeer(peerDomain, configJson);
        FederationTrafficSampleEntity latestSample = federationTrafficSampleJpaRepository.findFirstByPeer_IdOrderBySampledAtDesc(peer.getId())
            .orElse(null);
        federationTrafficSampleJpaRepository.saveAndFlush(new FederationTrafficSampleEntity(
            UUID.randomUUID(),
            peer,
            sampledAt,
            (latestSample == null ? 0 : latestSample.getInboundMessages()) + inboundMessagesDelta,
            (latestSample == null ? 0 : latestSample.getOutboundMessages()) + outboundMessagesDelta,
            (latestSample == null ? 0 : latestSample.getInboundStanzas()) + inboundStanzasDelta,
            (latestSample == null ? 0 : latestSample.getOutboundStanzas()) + outboundStanzasDelta,
            (latestSample == null ? 0 : latestSample.getErrorCount()) + errorDelta
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JabberConnectionSnapshot> listJabberConnections() {
        return xmppClientSessionJpaRepository.findTop100ByOrderByConnectedAtDesc().stream()
            .map(entity -> new JabberConnectionSnapshot(
                entity.getId(),
                entity.getJid() == null ? "" : entity.getJid(),
                JabberConnectionStatus.valueOf(entity.getStatus()),
                entity.getConnectedAt(),
                entity.getRemoteAddress() == null ? "" : entity.getRemoteAddress()
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FederationPeerSnapshot> listFederationPeers() {
        return federationPeerJpaRepository.findAllByOrderByPeerDomainAsc().stream()
            .map(entity -> new FederationPeerSnapshot(
                entity.getPeerDomain(),
                FederationPeerStatus.valueOf(entity.getStatus()),
                entity.getLastConnectedAt(),
                entity.getLastErrorAt()
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FederationTrafficSnapshot> listTrafficSnapshots() {
        return federationTrafficSampleJpaRepository.findTop200ByOrderBySampledAtDesc().stream()
            .map(entity -> new FederationTrafficSnapshot(
                entity.getPeer().getPeerDomain(),
                entity.getInboundMessages(),
                entity.getOutboundMessages(),
                entity.getInboundStanzas(),
                entity.getOutboundStanzas(),
                entity.getErrorCount(),
                entity.getSampledAt()
            ))
            .toList();
    }

    private FederationPeerEntity getOrCreatePeer(String peerDomain, String configJson) {
        String normalizedPeerDomain = requiredText(peerDomain, "peerDomain").toLowerCase(Locale.ROOT);
        return federationPeerJpaRepository.findByPeerDomain(normalizedPeerDomain)
            .orElseGet(() -> federationPeerJpaRepository.saveAndFlush(new FederationPeerEntity(
                UUID.randomUUID(),
                normalizedPeerDomain,
                FederationPeerStatus.DEGRADED.name(),
                null,
                null,
                configJson == null || configJson.isBlank() ? "{}" : configJson
            )));
    }

    private static Instant requiredConnectedAt(XmppClientSessionRecord record) {
        if (record.connectedAt() != null) {
            return record.connectedAt();
        }
        if (record.disconnectedAt() != null) {
            return record.disconnectedAt();
        }
        throw new IllegalArgumentException("connectedAt is required for new XMPP client sessions.");
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value;
    }

    private static String normalizedText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Instant latest(Instant currentValue, Instant candidate) {
        if (currentValue == null) {
            return candidate;
        }
        if (candidate == null || candidate.isBefore(currentValue)) {
            return currentValue;
        }
        return candidate;
    }
}
