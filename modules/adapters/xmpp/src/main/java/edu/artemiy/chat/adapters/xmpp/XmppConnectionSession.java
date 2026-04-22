package edu.artemiy.chat.adapters.xmpp;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

record XmppConnectionSession(
    UUID userId,
    String username,
    String displayName,
    String resource,
    String bareJid,
    String fullJid,
    Instant connectedAt,
    String remoteAddress,
    XmppConnectionHandler handler,
    AtomicBoolean available
) {

    boolean markAvailable() {
        return available.compareAndSet(false, true);
    }

    boolean markUnavailable() {
        return available.getAndSet(false);
    }

    boolean isAvailable() {
        return available.get();
    }
}
