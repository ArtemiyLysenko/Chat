package edu.artemiy.chat.app.http;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.artemiy.chat.admin.api.AdminObservabilityQuery;
import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;

@RestController
class JabberAdminController {

    private final AdminObservabilityQuery adminObservabilityQuery;

    JabberAdminController(AdminObservabilityQuery adminObservabilityQuery) {
        this.adminObservabilityQuery = adminObservabilityQuery;
    }

    @GetMapping("/api/admin/jabber/connections")
    List<JabberConnectionSnapshot> jabberConnections() {
        return adminObservabilityQuery.jabberConnections();
    }

    @GetMapping("/api/admin/jabber/federation/peers")
    List<FederationPeerSnapshot> federationPeers() {
        return adminObservabilityQuery.federationPeers();
    }

    @GetMapping("/api/admin/jabber/federation/traffic")
    List<FederationTrafficSnapshot> federationTraffic() {
        return adminObservabilityQuery.federationTraffic();
    }
}
