package edu.artemiy.chat.federation.api;

import java.util.UUID;

public interface FederationTelemetry {

    void recordXmppClientAuthenticating(String sessionId, String remoteAddress);

    void recordXmppClientConnected(
        String sessionId,
        UUID userId,
        String jid,
        String resource,
        String remoteAddress
    );

    void recordXmppClientClosed(String sessionId, JabberConnectionStatus status);

    void recordFederationInboundMessage(String peerDomain, String configJson);

    void recordFederationOutboundMessage(String peerDomain, String configJson);

    void recordFederationInboundRejectedMessage(String peerDomain, String configJson);

    void recordFederationOutboundRejectedMessage(String peerDomain, String configJson);

    void recordFederationError(String peerDomain, String configJson);
}
