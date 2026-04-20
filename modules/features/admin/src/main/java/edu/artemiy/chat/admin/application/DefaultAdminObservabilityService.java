package edu.artemiy.chat.admin.application;

import java.util.List;

import edu.artemiy.chat.admin.api.AdminObservabilityQuery;
import edu.artemiy.chat.federation.api.FederationAdminQuery;
import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;

public final class DefaultAdminObservabilityService implements AdminObservabilityQuery {

    private final FederationAdminQuery federationAdminQuery;

    public DefaultAdminObservabilityService(FederationAdminQuery federationAdminQuery) {
        this.federationAdminQuery = federationAdminQuery;
    }

    @Override
    public List<JabberConnectionSnapshot> jabberConnections() {
        return federationAdminQuery.listJabberConnections();
    }

    @Override
    public List<FederationPeerSnapshot> federationPeers() {
        return federationAdminQuery.listFederationPeers();
    }

    @Override
    public List<FederationTrafficSnapshot> federationTraffic() {
        return federationAdminQuery.listTrafficSnapshots();
    }
}
