package edu.artemiy.chat.admin.api;

import edu.artemiy.chat.admin.application.DefaultAdminObservabilityService;
import edu.artemiy.chat.federation.api.FederationAdminQuery;

public final class AdminFeatureFactory {

    private AdminFeatureFactory() {
    }

    public static AdminObservabilityQuery observabilityQuery(FederationAdminQuery federationAdminQuery) {
        return new DefaultAdminObservabilityService(federationAdminQuery);
    }
}
