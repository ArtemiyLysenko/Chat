package edu.artemiy.chat.loadtest.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class LoadTestConfigTests {

    @Test
    void parsesDefaultsForFederationComposeTopology() {
        LoadTestConfig config = LoadTestConfig.parse(new String[0]);

        assertThat(config.clientCountPerSide()).isEqualTo(50);
        assertThat(config.nodeA().httpBaseUrl()).isEqualTo("http://localhost:8081");
        assertThat(config.nodeA().xmppPort()).isEqualTo(5223);
        assertThat(config.nodeA().xmppDomain()).isEqualTo("node-a.local");
        assertThat(config.nodeB().httpBaseUrl()).isEqualTo("http://localhost:8082");
        assertThat(config.nodeB().xmppPort()).isEqualTo(5224);
        assertThat(config.deliveryTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(config.outputPath()).isEqualTo(Path.of("tools/load-tests/federation/build/reports/federation-load-%s.json".formatted(config.runId())));
    }

    @Test
    void parsesExplicitOverrides() {
        LoadTestConfig config = LoadTestConfig.parse(new String[] {
            "--run-id=custom-run",
            "--client-count=12",
            "--username-prefix=bench",
            "--password=secret",
            "--delivery-timeout-seconds=45",
            "--output=docs/evidence/custom.json",
            "--a-http=http://a.example",
            "--a-xmpp-host=a-host",
            "--a-xmpp-port=6123",
            "--a-domain=a.local",
            "--b-http=http://b.example",
            "--b-xmpp-host=b-host",
            "--b-xmpp-port=6124",
            "--b-domain=b.local"
        });

        assertThat(config.runId()).isEqualTo("custom-run");
        assertThat(config.clientCountPerSide()).isEqualTo(12);
        assertThat(config.usernamePrefix()).isEqualTo("bench");
        assertThat(config.password()).isEqualTo("secret");
        assertThat(config.deliveryTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(config.outputPath()).isEqualTo(Path.of("docs/evidence/custom.json"));
        assertThat(config.nodeA().xmppHost()).isEqualTo("a-host");
        assertThat(config.nodeB().xmppDomain()).isEqualTo("b.local");
    }

    @Test
    void rejectsMalformedArguments() {
        assertThatThrownBy(() -> LoadTestConfig.parse(new String[] { "--client-count" }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--key=value");

        assertThatThrownBy(() -> LoadTestConfig.parse(new String[] { "--client-count=nope" }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("client-count");
    }
}
