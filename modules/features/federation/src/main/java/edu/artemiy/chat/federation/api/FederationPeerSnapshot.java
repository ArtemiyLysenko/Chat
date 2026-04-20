package edu.artemiy.chat.federation.api;

import java.time.Instant;

public record FederationPeerSnapshot(
    String peerDomain,
    FederationPeerStatus status,
    Instant lastConnectedAt,
    Instant lastErrorAt
) {
}
