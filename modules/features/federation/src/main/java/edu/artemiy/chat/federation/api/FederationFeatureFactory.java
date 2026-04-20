package edu.artemiy.chat.federation.api;

import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.federation.application.StaticFederationAdminService;

public final class FederationFeatureFactory {

    private FederationFeatureFactory() {
    }

    public static FederationAdminQuery adminQuery(
        ClockPort clockPort,
        String nodeId,
        boolean federationEnabled,
        String peerDomain
    ) {
        return new StaticFederationAdminService(clockPort, nodeId, federationEnabled, peerDomain);
    }
}
