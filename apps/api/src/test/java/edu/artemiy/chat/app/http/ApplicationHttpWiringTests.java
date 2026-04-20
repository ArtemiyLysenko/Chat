package edu.artemiy.chat.app.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import edu.artemiy.chat.app.bootstrap.ChatApplication;

@SpringBootTest(
    classes = ChatApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "chat.node-id=test-node",
        "chat.federation.enabled=true",
        "chat.federation.peer-domain=federated.test"
    }
)
class ApplicationHttpWiringTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void bootstrapEndpointExposesModularArchitecture() throws Exception {
        String responseBody = get("/api/bootstrap");

        assertThat(responseBody).contains("\"nodeId\":\"test-node\"");
        assertThat(responseBody).contains("\"architecture\":\"modular-monolith-with-protocol-adapters\"");
    }

    @Test
    void jabberConnectionDashboardEndpointReturnsPayload() throws Exception {
        String responseBody = get("/api/admin/jabber/connections");

        assertThat(responseBody).contains("\"status\":\"CONNECTED\"");
    }

    @Test
    void federationTrafficEndpointReturnsPayload() throws Exception {
        String responseBody = get("/api/admin/jabber/federation/traffic");

        assertThat(responseBody).contains("\"peerDomain\":\"federated.test\"");
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
