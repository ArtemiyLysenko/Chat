package edu.artemiy.chat.federation.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationPeerStatus;
import edu.artemiy.chat.federation.api.FederationService;
import edu.artemiy.chat.federation.api.FederationSettings;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionStatus;
import edu.artemiy.chat.federation.spi.FederationPersistencePort;
import edu.artemiy.chat.federation.spi.XmppClientSessionRecord;

@Service
public final class DefaultFederationService implements FederationService {

    private final ClockPort clockPort;
    private final FederationSettings federationSettings;
    private final FederationPersistencePort federationPersistencePort;

    public DefaultFederationService(
        ClockPort clockPort,
        FederationSettings federationSettings,
        FederationPersistencePort federationPersistencePort
    ) {
        this.clockPort = clockPort;
        this.federationSettings = federationSettings;
        this.federationPersistencePort = federationPersistencePort;
    }

    @Override
    public void recordXmppClientAuthenticating(String sessionId, String remoteAddress) {
        federationPersistencePort.upsertXmppClientSession(new XmppClientSessionRecord(
            sessionId,
            null,
            null,
            null,
            JabberConnectionStatus.AUTHENTICATING,
            clockPort.now(),
            null,
            remoteAddress,
            federationSettings.nodeId()
        ));
    }

    @Override
    public void recordXmppClientConnected(String sessionId, UUID userId, String jid, String resource, String remoteAddress) {
        federationPersistencePort.upsertXmppClientSession(new XmppClientSessionRecord(
            sessionId,
            userId,
            jid,
            resource,
            JabberConnectionStatus.CONNECTED,
            clockPort.now(),
            null,
            remoteAddress,
            federationSettings.nodeId()
        ));
    }

    @Override
    public void recordXmppClientClosed(String sessionId, JabberConnectionStatus status) {
        federationPersistencePort.upsertXmppClientSession(new XmppClientSessionRecord(
            sessionId,
            null,
            null,
            null,
            status,
            null,
            clockPort.now(),
            null,
            federationSettings.nodeId()
        ));
    }

    @Override
    public void recordFederationInboundMessage(String peerDomain, String configJson) {
        federationPersistencePort.upsertFederationPeer(peerDomain, FederationPeerStatus.UP, clockPort.now(), null, configJson);
        federationPersistencePort.appendFederationTrafficSample(peerDomain, configJson, 1, 0, 1, 0, 0, clockPort.now());
    }

    @Override
    public void recordFederationOutboundMessage(String peerDomain, String configJson) {
        federationPersistencePort.upsertFederationPeer(peerDomain, FederationPeerStatus.UP, clockPort.now(), null, configJson);
        federationPersistencePort.appendFederationTrafficSample(peerDomain, configJson, 0, 1, 0, 1, 0, clockPort.now());
    }

    @Override
    public void recordFederationInboundRejectedMessage(String peerDomain, String configJson) {
        federationPersistencePort.upsertFederationPeer(peerDomain, FederationPeerStatus.UP, clockPort.now(), null, configJson);
        federationPersistencePort.appendFederationTrafficSample(peerDomain, configJson, 0, 0, 1, 0, 1, clockPort.now());
    }

    @Override
    public void recordFederationOutboundRejectedMessage(String peerDomain, String configJson) {
        federationPersistencePort.upsertFederationPeer(peerDomain, FederationPeerStatus.UP, clockPort.now(), null, configJson);
        federationPersistencePort.appendFederationTrafficSample(peerDomain, configJson, 0, 0, 0, 1, 1, clockPort.now());
    }

    @Override
    public void recordFederationError(String peerDomain, String configJson) {
        federationPersistencePort.upsertFederationPeer(peerDomain, FederationPeerStatus.DOWN, null, clockPort.now(), configJson);
        federationPersistencePort.appendFederationTrafficSample(peerDomain, configJson, 0, 0, 0, 0, 1, clockPort.now());
    }

    @Override
    public List<JabberConnectionSnapshot> listJabberConnections() {
        return federationPersistencePort.listJabberConnections();
    }

    @Override
    public List<FederationPeerSnapshot> listFederationPeers() {
        return federationPersistencePort.listFederationPeers();
    }

    @Override
    public List<FederationTrafficSnapshot> listTrafficSnapshots() {
        return federationPersistencePort.listTrafficSnapshots();
    }
}
