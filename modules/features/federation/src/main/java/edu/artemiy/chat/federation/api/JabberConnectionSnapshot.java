package edu.artemiy.chat.federation.api;

import java.time.Instant;

public record JabberConnectionSnapshot(
    String sessionId,
    String principal,
    JabberConnectionStatus status,
    Instant connectedAt,
    String remoteAddress
) {
}
