package edu.artemiy.chat.app.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import edu.artemiy.chat.app.bootstrap.ChatApplication;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(
    classes = ChatApplication.class,
    properties = {
        "chat.node-id=test-node",
        "chat.federation.enabled=true",
        "chat.federation.peer-domain=federated.test"
    }
)
@ActiveProfiles("test")
class MessagingHttpIntegrationTests extends PostgresIntegrationSupport {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry);
    }

    @BeforeEach
    void cleanDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters(springSecurityFilterChain)
            .build();
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
    void postRoomMessageCreatesMessage() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Bridge");

        JsonNode created = postJson(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":"Hello room"}
                """,
            captain,
            201
        );

        assertThat(created.path("chat").path("type").asText()).isEqualTo("ROOM");
        assertThat(created.path("chat").path("id").asText()).isEqualTo(roomId.toString());
        assertThat(created.path("author").path("username").asText()).isEqualTo("captain");
        assertThat(created.path("bodyText").asText()).isEqualTo("Hello room");
        assertThat(created.path("state").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void postRoomMessageRejectsNullBodyAsBadRequest() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Bridge");

        MockHttpServletResponse response = postJsonRaw(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":null}
                """,
            captain.csrfCookie(),
            captain.sessionCookie(),
            400
        ).getResponse();

        assertThat(objectMapper.readTree(response.getContentAsString()).path("code").asText())
            .isEqualTo("request.validation_failed");
    }

    @Test
    void getMessagesReturnsChronologicalPagesAndSupportsBeforeCursor() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Bridge");

        postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"One\"}", captain, 201);
        postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"Two\"}", captain, 201);
        postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"Three\"}", captain, 201);

        JsonNode newestPage = getJson("/api/chats/room/%s/messages?limit=2".formatted(roomId), captain);
        JsonNode olderPage = getJson(
            "/api/chats/room/%s/messages?limit=2&before=%s".formatted(roomId, newestPage.path("nextBeforeMessageId").asText()),
            captain
        );

        assertThat(newestPage.path("items")).extracting(JsonNode::asText);
        assertThat(newestPage.path("items").get(0).path("bodyText").asText()).isEqualTo("Two");
        assertThat(newestPage.path("items").get(1).path("bodyText").asText()).isEqualTo("Three");
        assertThat(newestPage.path("nextBeforeMessageId").asText()).isNotBlank();
        assertThat(olderPage.path("items").size()).isEqualTo(1);
        assertThat(olderPage.path("items").get(0).path("bodyText").asText()).isEqualTo("One");
        assertThat(olderPage.path("nextBeforeMessageId").isNull()).isTrue();
    }

    @Test
    void postReadMarkersAdvancesOnlyForward() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Bridge");

        JsonNode first = postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"One\"}", captain, 201);
        JsonNode second = postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"Two\"}", captain, 201);

        JsonNode advanced = postJson(
            "/api/chats/room/%s/read-markers".formatted(roomId),
            """
                {"lastReadMessageId":"%s"}
                """.formatted(second.path("id").asText()),
            captain,
            200
        );
        JsonNode unchanged = postJson(
            "/api/chats/room/%s/read-markers".formatted(roomId),
            """
                {"lastReadMessageId":"%s"}
                """.formatted(first.path("id").asText()),
            captain,
            200
        );

        assertThat(advanced.path("lastReadMessageId").asText()).isEqualTo(second.path("id").asText());
        assertThat(unchanged.path("lastReadMessageId").asText()).isEqualTo(second.path("id").asText());
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from chat_unread_markers where room_id = ? and user_id = ?",
            Integer.class,
            roomId,
            captain.userId()
        )).isEqualTo(1);
    }

    @Test
    void roomAuthorizationFailuresReturnForbidden() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient outsider = registerAndLogin("outsider@example.com", "outsider");
        UUID roomId = createRoom(captain, "Bridge");

        MockHttpServletResponse historyDenied = getJsonRaw("/api/chats/room/%s/messages".formatted(roomId), outsider, 403).getResponse();
        MockHttpServletResponse sendDenied = postJsonRaw(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":"Intrude"}
                """,
            outsider.csrfCookie(),
            outsider.sessionCookie(),
            403
        ).getResponse();

        assertThat(objectMapper.readTree(historyDenied.getContentAsString()).path("code").asText())
            .isEqualTo("messaging.room_membership_required");
        assertThat(objectMapper.readTree(sendDenied.getContentAsString()).path("code").asText())
            .isEqualTo("messaging.room_membership_required");
    }

    @Test
    void directDialogHistoryRemainsReadableAfterBlockWhileSendBecomesForbidden() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");
        makeFriends(captain, scout);
        JsonNode dialog = postJson("/api/direct-dialogs/%s".formatted(scout.userId()), null, captain, 201);
        UUID directDialogId = UUID.fromString(dialog.path("dialogId").asText());

        postJson(
            "/api/chats/direct/%s/messages".formatted(directDialogId),
            """
                {"bodyText":"Still there"}
                """,
            captain,
            201
        );
        putWithoutBody("/api/blocks/%s".formatted(scout.userId()), captain, 204);

        JsonNode history = getJson("/api/chats/direct/%s/messages".formatted(directDialogId), captain);
        MockHttpServletResponse deniedSend = postJsonRaw(
            "/api/chats/direct/%s/messages".formatted(directDialogId),
            """
                {"bodyText":"Denied now"}
                """,
            captain.csrfCookie(),
            captain.sessionCookie(),
            403
        ).getResponse();

        assertThat(history.path("items").size()).isEqualTo(1);
        assertThat(history.path("items").get(0).path("bodyText").asText()).isEqualTo("Still there");
        assertThat(objectMapper.readTree(deniedSend.getContentAsString()).path("code").asText())
            .isEqualTo("messaging.direct_dialog_blocked");
    }

    private void makeFriends(AuthenticatedClient first, AuthenticatedClient second) throws Exception {
        JsonNode request = postJson(
            "/api/friend-requests",
            """
                {"userId":"%s"}
                """.formatted(second.userId()),
            first,
            201
        );
        postWithoutBody("/api/friend-requests/%s/accept".formatted(request.path("requestId").asText()), second, 204);
    }

    private UUID createRoom(AuthenticatedClient client, String name) throws Exception {
        JsonNode created = postJson(
            "/api/rooms",
            """
                {"name":"%s","description":null,"visibility":"PUBLIC"}
                """.formatted(name),
            client,
            201
        );
        return UUID.fromString(created.path("id").asText());
    }

    private AuthenticatedClient registerAndLogin(String email, String username) throws Exception {
        Cookie csrfCookie = primeCsrfCookie();

        MockHttpServletResponse registerResponse = postJsonRaw(
            "/api/auth/register",
            """
                {"email":"%s","username":"%s","password":"password123"}
                """.formatted(email, username),
            csrfCookie,
            null,
            201
        ).getResponse();
        UUID userId = UUID.fromString(objectMapper.readTree(registerResponse.getContentAsString()).path("id").asText());

        MockHttpServletResponse loginResponse = postJsonRaw(
            "/api/auth/login",
            """
                {"email":"%s","password":"password123"}
                """.formatted(email),
            csrfCookie,
            null,
            200
        ).getResponse();

        Cookie sessionCookie = requiredCookie(loginResponse, "CHAT_SESSION");
        Cookie refreshedCsrfCookie = optionalCookie(loginResponse, "XSRF-TOKEN");
        return new AuthenticatedClient(userId, sessionCookie, refreshedCsrfCookie == null ? csrfCookie : refreshedCsrfCookie);
    }

    private Cookie primeCsrfCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
            post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"noop@example.com\",\"username\":\"noop\",\"password\":\"password123\"}")
        ).andExpect(status().isForbidden())
            .andReturn()
            .getResponse();
        return requiredCookie(response, "XSRF-TOKEN");
    }

    private JsonNode getJson(String path, AuthenticatedClient client) throws Exception {
        return objectMapper.readTree(getJsonRaw(path, client, 200).getResponse().getContentAsString());
    }

    private MvcResult getJsonRaw(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
        return mockMvc.perform(get(path).cookie(client.sessionCookie()))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    }

    private JsonNode postJson(String path, String body, AuthenticatedClient client, int expectedStatus) throws Exception {
        return objectMapper.readTree(postJsonRaw(path, body, client.csrfCookie(), client.sessionCookie(), expectedStatus)
            .getResponse()
            .getContentAsString());
    }

    private void postWithoutBody(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
        postJsonRaw(path, null, client.csrfCookie(), client.sessionCookie(), expectedStatus);
    }

    private void putWithoutBody(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
        mockMvc.perform(
            put(path)
                .contentType(APPLICATION_JSON)
                .header("X-CSRF-TOKEN", client.csrfCookie().getValue())
                .cookie(client.csrfCookie(), client.sessionCookie())
        ).andExpect(status().is(expectedStatus));
    }

    private MvcResult postJsonRaw(String path, String body, Cookie csrfCookie, Cookie sessionCookie, int expectedStatus) throws Exception {
        var request = post(path)
            .contentType(APPLICATION_JSON)
            .header("X-CSRF-TOKEN", csrfCookie.getValue());
        if (body != null) {
            request.content(body);
        }
        request.cookie(csrfCookie);
        if (sessionCookie != null) {
            request.cookie(sessionCookie);
        }
        return mockMvc.perform(request)
            .andExpect(status().is(expectedStatus))
            .andReturn();
    }

    private static Cookie requiredCookie(MockHttpServletResponse response, String name) {
        Cookie cookie = response.getCookie(name);
        assertThat(cookie).as("Expected cookie %s", name).isNotNull();
        return cookie;
    }

    private static Cookie optionalCookie(MockHttpServletResponse response, String name) {
        return response.getCookie(name);
    }

    private record AuthenticatedClient(UUID userId, Cookie sessionCookie, Cookie csrfCookie) {
    }
}
