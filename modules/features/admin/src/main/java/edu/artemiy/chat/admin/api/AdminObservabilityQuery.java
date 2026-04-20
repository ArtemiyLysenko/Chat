package edu.artemiy.chat.admin.api;

import java.util.List;

import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;

public interface AdminObservabilityQuery {

    List<JabberConnectionSnapshot> jabberConnections();

    List<FederationPeerSnapshot> federationPeers();

    List<FederationTrafficSnapshot> federationTraffic();
}
