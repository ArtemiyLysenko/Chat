package edu.artemiy.chat.federation.api;

import java.util.List;

public interface FederationAdminQuery {

    List<JabberConnectionSnapshot> listJabberConnections();

    List<FederationPeerSnapshot> listFederationPeers();

    List<FederationTrafficSnapshot> listTrafficSnapshots();
}
