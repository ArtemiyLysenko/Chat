package edu.artemiy.chat.federation.application;

import java.time.Instant;
import java.util.List;

import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.federation.api.FederationAdminQuery;
import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationPeerStatus;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionStatus;

public final class StaticFederationAdminService implements FederationAdminQuery {

    private final ClockPort clockPort;
    private final String nodeId;
    private final boolean federationEnabled;
    private final String peerDomain;

    public StaticFederationAdminService(
        ClockPort clockPort,
        String nodeId,
        boolean federationEnabled,
        String peerDomain
    ) {
        this.clockPort = clockPort;
        this.nodeId = nodeId;
        this.federationEnabled = federationEnabled;
        this.peerDomain = peerDomain == null ? "" : peerDomain;
    }

    @Override
    public List<JabberConnectionSnapshot> listJabberConnections() {
        Instant now = clockPort.now();
        return List.of(new JabberConnectionSnapshot(
            nodeId + "-bootstrap-session",
            "bootstrap@" + nodeId,
            federationEnabled ? JabberConnectionStatus.CONNECTED : JabberConnectionStatus.DISCONNECTED,
            now,
            "127.0.0.1"
        ));
    }

    @Override
    public List<FederationPeerSnapshot> listFederationPeers() {
        Instant now = clockPort.now();
        String effectivePeerDomain = peerDomain.isBlank() ? "peer." + nodeId + ".local" : peerDomain;
        return List.of(new FederationPeerSnapshot(
            effectivePeerDomain,
            federationEnabled ? FederationPeerStatus.UP : FederationPeerStatus.DEGRADED,
            federationEnabled ? now : null,
            federationEnabled ? null : now
        ));
    }

    @Override
    public List<FederationTrafficSnapshot> listTrafficSnapshots() {
        Instant now = clockPort.now();
        String effectivePeerDomain = peerDomain.isBlank() ? "peer." + nodeId + ".local" : peerDomain;
        long outboundMessages = federationEnabled ? 50L : 0L;
        long inboundMessages = federationEnabled ? 50L : 0L;
        return List.of(new FederationTrafficSnapshot(
            effectivePeerDomain,
            inboundMessages,
            outboundMessages,
            inboundMessages,
            outboundMessages,
            federationEnabled ? 0L : 1L,
            now
        ));
    }
}
