package edu.artemiy.chat.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import edu.artemiy.chat.federation.api.JabberConnectionStatus;
import edu.artemiy.chat.testing.FixedClock;

class StaticFederationAdminServiceTests {

    @Test
    void returnsConnectedSnapshotsWhenFederationIsEnabled() {
        StaticFederationAdminService service = new StaticFederationAdminService(
            new FixedClock(Instant.parse("2026-04-20T15:00:00Z")),
            "node-a",
            true,
            "node-b.local"
        );

        assertThat(service.listJabberConnections())
            .singleElement()
            .extracting(snapshot -> snapshot.status())
            .isEqualTo(JabberConnectionStatus.CONNECTED);
        assertThat(service.listTrafficSnapshots())
            .singleElement()
            .extracting(snapshot -> snapshot.peerDomain())
            .isEqualTo("node-b.local");
    }
}
