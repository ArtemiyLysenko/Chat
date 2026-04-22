package edu.artemiy.chat.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationPeerStatus;
import edu.artemiy.chat.federation.api.FederationSettings;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionStatus;
import edu.artemiy.chat.federation.spi.FederationPersistencePort;
import edu.artemiy.chat.federation.spi.XmppClientSessionRecord;
import edu.artemiy.chat.testing.FixedClock;

class DefaultFederationServiceTests {

    @Test
    void recordsXmppSessionsPeerStateAndTraffic() {
        Instant now = Instant.parse("2026-04-22T12:00:00Z");
        FakeFederationPersistencePort federationPersistencePort = new FakeFederationPersistencePort();
        DefaultFederationService service = new DefaultFederationService(
            new FixedClock(now),
            new FederationSettings("node-a"),
            federationPersistencePort
        );

        service.recordXmppClientAuthenticating("session-1", "127.0.0.1");
        service.recordXmppClientConnected("session-1", UUID.randomUUID(), "captain@node-a.local/desk", "desk", "127.0.0.1");
        service.recordFederationOutboundMessage("node-b.local", "{\"host\":\"127.0.0.1\",\"port\":5224}");
        service.recordFederationInboundMessage("node-b.local", "{\"host\":\"127.0.0.1\",\"port\":5224}");
        service.recordFederationOutboundRejectedMessage("node-b.local", "{\"host\":\"127.0.0.1\",\"port\":5224}");
        service.recordFederationInboundRejectedMessage("node-b.local", "{\"host\":\"127.0.0.1\",\"port\":5224}");
        service.recordFederationError("node-b.local", "{\"host\":\"127.0.0.1\",\"port\":5224}");
        service.recordXmppClientClosed("session-1", JabberConnectionStatus.DISCONNECTED);

        assertThat(federationPersistencePort.sessionRecords)
            .extracting(XmppClientSessionRecord::status)
            .containsExactly(
                JabberConnectionStatus.AUTHENTICATING,
                JabberConnectionStatus.CONNECTED,
                JabberConnectionStatus.DISCONNECTED
            );
        assertThat(federationPersistencePort.peerSnapshots)
            .extracting(FederationPeerSnapshot::status)
            .containsExactly(
                FederationPeerStatus.UP,
                FederationPeerStatus.UP,
                FederationPeerStatus.UP,
                FederationPeerStatus.UP,
                FederationPeerStatus.DOWN
            );
        assertThat(federationPersistencePort.trafficSnapshots)
            .extracting(snapshot -> List.of(
                snapshot.inboundMessages(),
                snapshot.outboundMessages(),
                snapshot.inboundStanzas(),
                snapshot.outboundStanzas(),
                snapshot.errorCount()
            ))
            .containsExactly(
                List.of(0L, 1L, 0L, 1L, 0L),
                List.of(1L, 0L, 1L, 0L, 0L),
                List.of(0L, 0L, 0L, 1L, 1L),
                List.of(0L, 0L, 1L, 0L, 1L),
                List.of(0L, 0L, 0L, 0L, 1L)
            );
        assertThat(service.listJabberConnections()).isSameAs(federationPersistencePort.jabberConnections);
        assertThat(service.listFederationPeers()).isSameAs(federationPersistencePort.peerSnapshots);
        assertThat(service.listTrafficSnapshots()).isSameAs(federationPersistencePort.trafficSnapshots);
    }

    private static final class FakeFederationPersistencePort implements FederationPersistencePort {

        private final List<XmppClientSessionRecord> sessionRecords = new ArrayList<>();
        private final List<JabberConnectionSnapshot> jabberConnections = new ArrayList<>();
        private final List<FederationPeerSnapshot> peerSnapshots = new ArrayList<>();
        private final List<FederationTrafficSnapshot> trafficSnapshots = new ArrayList<>();

        @Override
        public void upsertXmppClientSession(XmppClientSessionRecord record) {
            sessionRecords.add(record);
            jabberConnections.add(new JabberConnectionSnapshot(
                record.sessionId(),
                record.jid() == null ? "" : record.jid(),
                record.status(),
                record.connectedAt(),
                record.remoteAddress() == null ? "" : record.remoteAddress()
            ));
        }

        @Override
        public void upsertFederationPeer(
            String peerDomain,
            FederationPeerStatus status,
            Instant lastConnectedAt,
            Instant lastErrorAt,
            String configJson
        ) {
            peerSnapshots.add(new FederationPeerSnapshot(peerDomain, status, lastConnectedAt, lastErrorAt));
        }

        @Override
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
            trafficSnapshots.add(new FederationTrafficSnapshot(
                peerDomain,
                inboundMessagesDelta,
                outboundMessagesDelta,
                inboundStanzasDelta,
                outboundStanzasDelta,
                errorDelta,
                sampledAt
            ));
        }

        @Override
        public List<JabberConnectionSnapshot> listJabberConnections() {
            return jabberConnections;
        }

        @Override
        public List<FederationPeerSnapshot> listFederationPeers() {
            return peerSnapshots;
        }

        @Override
        public List<FederationTrafficSnapshot> listTrafficSnapshots() {
            return trafficSnapshots;
        }
    }
}
