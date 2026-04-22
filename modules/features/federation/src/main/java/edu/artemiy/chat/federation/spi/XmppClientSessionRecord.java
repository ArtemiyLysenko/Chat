package edu.artemiy.chat.federation.spi;

import java.time.Instant;
import java.util.UUID;

import edu.artemiy.chat.federation.api.JabberConnectionStatus;

public record XmppClientSessionRecord(
    String sessionId,
    UUID userId,
    String jid,
    String resource,
    JabberConnectionStatus status,
    Instant connectedAt,
    Instant disconnectedAt,
    String remoteAddress,
    String serverNode
) {
}
