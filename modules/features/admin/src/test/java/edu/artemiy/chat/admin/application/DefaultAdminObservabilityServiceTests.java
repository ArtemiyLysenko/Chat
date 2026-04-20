package edu.artemiy.chat.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.artemiy.chat.federation.api.FederationAdminQuery;
import edu.artemiy.chat.federation.api.FederationPeerSnapshot;
import edu.artemiy.chat.federation.api.FederationPeerStatus;
import edu.artemiy.chat.federation.api.FederationTrafficSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionSnapshot;
import edu.artemiy.chat.federation.api.JabberConnectionStatus;

class DefaultAdminObservabilityServiceTests {

    @Test
    void delegatesToFederationQueries() {
        FederationAdminQuery federationAdminQuery = new FederationAdminQuery() {
            @Override
            public List<JabberConnectionSnapshot> listJabberConnections() {
                return List.of(new JabberConnectionSnapshot(
                    "session-1",
                    "bootstrap@test",
                    JabberConnectionStatus.CONNECTED,
                    Instant.parse("2026-04-20T15:00:00Z"),
                    "127.0.0.1"
                ));
            }

            @Override
            public List<FederationPeerSnapshot> listFederationPeers() {
                return List.of(new FederationPeerSnapshot(
                    "peer.test",
                    FederationPeerStatus.UP,
                    Instant.parse("2026-04-20T15:00:00Z"),
                    null
                ));
            }

            @Override
            public List<FederationTrafficSnapshot> listTrafficSnapshots() {
                return List.of(new FederationTrafficSnapshot(
                    "peer.test",
                    50,
                    50,
                    50,
                    50,
                    0,
                    Instant.parse("2026-04-20T15:00:00Z")
                ));
            }
        };

        DefaultAdminObservabilityService service = new DefaultAdminObservabilityService(federationAdminQuery);

        assertThat(service.jabberConnections()).hasSize(1);
        assertThat(service.federationPeers()).hasSize(1);
        assertThat(service.federationTraffic()).hasSize(1);
    }
}
