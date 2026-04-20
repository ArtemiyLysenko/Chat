package edu.artemiy.chat.adapters.xmpp;

import edu.artemiy.chat.federation.api.FederationPeerStatus;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.presence.api.PresenceState;

public final class XmppProtocolBridge {

    public String describeInboundRoute(ChatTargetRef chatTargetRef) {
        return "xmpp:" + chatTargetRef.type().name().toLowerCase() + ":" + chatTargetRef.id();
    }

    public PresenceState normalizePresence(PresenceState presenceState) {
        return presenceState;
    }

    public FederationPeerStatus health(boolean connected) {
        return connected ? FederationPeerStatus.UP : FederationPeerStatus.DOWN;
    }
}
