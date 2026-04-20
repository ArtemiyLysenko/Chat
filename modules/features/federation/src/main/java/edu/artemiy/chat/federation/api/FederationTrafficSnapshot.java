package edu.artemiy.chat.federation.api;

import java.time.Instant;

public record FederationTrafficSnapshot(
    String peerDomain,
    long inboundMessages,
    long outboundMessages,
    long inboundStanzas,
    long outboundStanzas,
    long errorCount,
    Instant sampledAt
) {
}
