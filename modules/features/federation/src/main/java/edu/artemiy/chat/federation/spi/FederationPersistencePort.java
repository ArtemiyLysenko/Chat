package edu.artemiy.chat.federation.spi;

import java.time.Instant;
import java.util.List;

import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationPeerStatus;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;

public interface FederationPersistencePort {

    void upsertXmppClientSession(XmppClientSessionRecord record);

    void upsertFederationPeer(
        String peerDomain,
        FederationPeerStatus status,
        Instant lastConnectedAt,
        Instant lastErrorAt,
        String configJson
    );

    void appendFederationTrafficSample(
        String peerDomain,
        String configJson,
        long inboundMessagesDelta,
        long outboundMessagesDelta,
        long inboundStanzasDelta,
        long outboundStanzasDelta,
        long errorDelta,
        Instant sampledAt
    );

    List<JabberConnectionSnapshot> listJabberConnections();

    List<FederationPeerSnapshot> listFederationPeers();

    List<FederationTrafficSnapshot> listTrafficSnapshots();
}
