package edu.artemiy.chat.app.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.app.bootstrap.ChatApplication;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    classes = ChatApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "chat.node-id=test-node",
        "chat.federation.enabled=true",
        "chat.federation.peer-domain=federated.test"
    }
)
@ActiveProfiles("test")
class ChatWebSocketIntegrationTests extends PostgresIntegrationSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry);
    }

    @BeforeEach
    void cleanDatabase() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        jdbcTemplate.execute(
            """
                truncate table
                    chat_unread_markers,
                    messages,
                    direct_dialogs,
                    user_blocks,
                    friendships,
                    friendship_requests,
                    moderation_audit_events,
                    room_bans,
                    room_invites,
                    room_memberships,
                    rooms,
                    user_sessions,
                    password_reset_tokens,
                    users
                cascade
                """
        );
    }

    @Test
    void rejectsUnauthenticatedHandshake() {
        BrowserSession browser = new BrowserSession();

        assertThatThrownBy(() -> connectWebSocket(browser))
            .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class);
    }

    @Test
    void fansOutMessageLifecycleAndUnreadUpdatesToAuthorizedRoomSockets() throws Exception {
        BrowserSession owner = new BrowserSession();
        BrowserSession member = new BrowserSession();
        registerAndLogin(owner, "owner@example.com", "owner");
        registerAndLogin(member, "member@example.com", "member");

        UUID roomId = createRoom(owner, "Bridge");
        member.postWithoutBody("/api/rooms/%s/join".formatted(roomId), 204);

        ConnectedSocket memberSocket = connectWebSocket(member);
        try {
            JsonNode created = owner.postJson(
                "/api/chats/room/%s/messages".formatted(roomId),
                """
                    {"bodyText":"Status green"}
                    """,
                201
            );

            JsonNode createdEvent = memberSocket.listener().awaitMessage();
            JsonNode unreadAfterCreate = memberSocket.listener().awaitMessage();
            assertThat(createdEvent.path("type").asText()).isEqualTo("message.created");
            assertThat(createdEvent.path("chat").path("type").asText()).isEqualTo("ROOM");
            assertThat(createdEvent.path("payload").path("id").asText()).isEqualTo(created.path("id").asText());
            assertThat(createdEvent.path("payload").path("bodyText").asText()).isEqualTo("Status green");
            assertThat(unreadAfterCreate.path("type").asText()).isEqualTo("unread.updated");
            assertThat(unreadAfterCreate.path("payload").path("unreadCount").asInt()).isEqualTo(1);

            owner.patchJson(
                "/api/messages/%s".formatted(created.path("id").asText()),
                """
                    {"bodyText":"Status amber"}
                    """,
                200
            );
            JsonNode updatedEvent = memberSocket.listener().awaitMessage();
            assertThat(updatedEvent.path("type").asText()).isEqualTo("message.updated");
            assertThat(updatedEvent.path("payload").path("bodyText").asText()).isEqualTo("Status amber");
            assertThat(updatedEvent.path("payload").path("state").asText()).isEqualTo("EDITED");

            owner.deleteJson("/api/messages/%s".formatted(created.path("id").asText()), 200);
            JsonNode deletedEvent = memberSocket.listener().awaitMessage();
            assertThat(deletedEvent.path("type").asText()).isEqualTo("message.deleted");
            assertThat(deletedEvent.path("payload").path("state").asText()).isEqualTo("DELETED");

            member.postJson(
                "/api/chats/room/%s/read-markers".formatted(roomId),
                """
                    {"lastReadMessageId":"%s"}
                    """.formatted(created.path("id").asText()),
                200
            );
            JsonNode unreadAfterRead = memberSocket.listener().awaitMessage();
            assertThat(unreadAfterRead.path("type").asText()).isEqualTo("unread.updated");
            assertThat(unreadAfterRead.path("payload").path("unreadCount").asInt()).isZero();
            assertThat(unreadAfterRead.path("payload").path("lastReadMessageId").asText()).isEqualTo(created.path("id").asText());
        }
        finally {
            memberSocket.webSocket().sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    void revokesLiveSocketWhenSessionIsRevoked() throws Exception {
        BrowserSession firstBrowser = new BrowserSession();
        BrowserSession secondBrowser = new BrowserSession();
        registerAndLogin(firstBrowser, "captain@example.com", "captain");
        registerAndLogin(secondBrowser, "captain@example.com", "captain", true);

        ConnectedSocket socket = connectWebSocket(firstBrowser);
        try {
            secondBrowser.deleteWithoutBody("/api/sessions/%s".formatted(firstBrowser.cookieValue("CHAT_SESSION")), 204);

            JsonNode revokedEvent = socket.listener().awaitMessage();
            assertThat(revokedEvent.path("type").asText()).isEqualTo("session.revoked");
            assertThat(revokedEvent.path("payload").path("sessionId").asText()).isEqualTo(firstBrowser.cookieValue("CHAT_SESSION"));

            int closeCode = socket.listener().awaitCloseCode();
            assertThat(closeCode).isEqualTo(4401);
        }
        finally {
            socket.webSocket().abort();
        }
    }

    private void registerAndLogin(BrowserSession browser, String email, String username) throws Exception {
        registerAndLogin(browser, email, username, false);
    }

    private void registerAndLogin(BrowserSession browser, String email, String username, boolean skipRegister) throws Exception {
        if (!skipRegister) {
            browser.get("/register");
            browser.postJson(
                "/api/auth/register",
                """
                    {"email":"%s","username":"%s","password":"password123"}
                    """.formatted(email, username),
                201
            );
        }
        browser.get("/login");
        browser.postJson(
            "/api/auth/login",
            """
                {"email":"%s","password":"password123"}
                """.formatted(email),
            200
        );
    }

    private UUID createRoom(BrowserSession browser, String roomName) throws Exception {
        JsonNode room = browser.postJson(
            "/api/rooms",
            """
                {"name":"%s","description":null,"visibility":"PUBLIC"}
                """.formatted(roomName),
            201
        );
        return UUID.fromString(room.path("id").asText());
    }

    private ConnectedSocket connectWebSocket(BrowserSession browser) throws Exception {
        TestWebSocketListener listener = new TestWebSocketListener(objectMapper);
        var builder = browser.httpClient.newWebSocketBuilder();
        String sessionCookie = browser.cookieValue("CHAT_SESSION");
        if (!sessionCookie.isBlank()) {
            builder.header("Cookie", "CHAT_SESSION=%s".formatted(sessionCookie));
        }
        WebSocket webSocket = builder.buildAsync(webSocketUri(), listener)
            .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        return new ConnectedSocket(webSocket, listener);
    }

    private URI webSocketUri() {
        return URI.create("ws://localhost:" + port + "/ws");
    }

    private final class BrowserSession {

        private final CookieManager cookieManager = new CookieManager();
        private final HttpClient httpClient = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(httpUri(path))
                .header("User-Agent", "TestBrowser/1.0")
                .GET()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(httpUri(path))
                .header("Content-Type", "application/json")
                .header("User-Agent", "TestBrowser/1.0")
                .header("X-CSRF-TOKEN", cookieValue("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
            return response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        }

        JsonNode patchJson(String path, String body, int expectedStatus) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(httpUri(path))
                .header("Content-Type", "application/json")
                .header("User-Agent", "TestBrowser/1.0")
                .header("X-CSRF-TOKEN", cookieValue("XSRF-TOKEN"))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
            return response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        }

        JsonNode deleteJson(String path, int expectedStatus) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(httpUri(path))
                .header("User-Agent", "TestBrowser/1.0")
                .header("X-CSRF-TOKEN", cookieValue("XSRF-TOKEN"))
                .DELETE()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
            return response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        }

        void postWithoutBody(String path, int expectedStatus) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(httpUri(path))
                .header("User-Agent", "TestBrowser/1.0")
                .header("X-CSRF-TOKEN", cookieValue("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
        }

        void deleteWithoutBody(String path, int expectedStatus) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(httpUri(path))
                .header("User-Agent", "TestBrowser/1.0")
                .header("X-CSRF-TOKEN", cookieValue("XSRF-TOKEN"))
                .DELETE()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
        }

        String cookieValue(String name) {
            Optional<HttpCookie> cookie = cookieManager.getCookieStore().getCookies().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst();
            return cookie.map(HttpCookie::getValue).orElse("");
        }

        private URI httpUri(String path) {
            return URI.create("http://localhost:" + port + path);
        }
    }

    private record ConnectedSocket(WebSocket webSocket, TestWebSocketListener listener) {
    }

    private static final class TestWebSocketListener implements WebSocket.Listener {

        private final ObjectMapper objectMapper;
        private final LinkedBlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        private final StringBuilder partialMessage = new StringBuilder();

        private TestWebSocketListener(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialMessage.append(data);
            if (last) {
                try {
                    messages.add(objectMapper.readTree(partialMessage.toString()));
                }
                catch (Exception exception) {
                    throw new IllegalStateException("Unable to parse WebSocket payload.", exception);
                }
                partialMessage.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode.complete(statusCode);
            return CompletableFuture.completedFuture(null);
        }

        JsonNode awaitMessage() throws Exception {
            JsonNode message = messages.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(message).as("Timed out waiting for a WebSocket message").isNotNull();
            return message;
        }

        int awaitCloseCode() throws Exception {
            return closeCode.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
    }
}
