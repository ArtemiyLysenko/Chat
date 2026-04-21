package edu.artemiy.chat.app.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ContactsHttpIntegrationTests extends PostgresIntegrationSupport {

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
    void createsFriendRequestByUsername() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");

        JsonNode created = postJson(
            "/api/friend-requests",
            """
                {"username":"scout","messageText":"Hello there"}
                """,
            captain,
            201
        );

        assertThat(created.path("outcome").asText()).isEqualTo("REQUEST_CREATED");
        assertThat(created.path("requestId").asText()).isNotBlank();
        assertThat(created.path("user").path("id").asText()).isEqualTo(scout.userId().toString());

        JsonNode contacts = getJson("/api/contacts", captain);
        assertThat(contacts.path("outboundPendingRequests").size()).isEqualTo(1);
        assertThat(contacts.path("outboundPendingRequests").get(0).path("user").path("username").asText()).isEqualTo("scout");
    }

    @Test
    void createsFriendRequestByCompatibilityEquivalentUsername() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");

        JsonNode created = postJson(
            "/api/friend-requests",
            """
                {"username":"ｓｃｏｕｔ"}
                """,
            captain,
            201
        );

        assertThat(created.path("outcome").asText()).isEqualTo("REQUEST_CREATED");
        assertThat(created.path("user").path("id").asText()).isEqualTo(scout.userId().toString());
        assertThat(getJson("/api/contacts", scout).path("inboundPendingRequests").size()).isEqualTo(1);
    }

    @Test
    void createsFriendRequestByUserId() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");

        JsonNode created = postJson(
            "/api/friend-requests",
            """
                {"userId":"%s"}
                """.formatted(scout.userId()),
            captain,
            201
        );

        assertThat(created.path("outcome").asText()).isEqualTo("REQUEST_CREATED");
        assertThat(getJson("/api/contacts", scout).path("inboundPendingRequests").size()).isEqualTo(1);
    }

    @Test
    void acceptsFriendRequest() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");

        JsonNode created = postJson(
            "/api/friend-requests",
            """
                {"username":"scout"}
                """,
            captain,
            201
        );

        postWithoutBody("/api/friend-requests/%s/accept".formatted(created.path("requestId").asText()), scout, 204);

        JsonNode scoutContacts = getJson("/api/contacts", scout);
        assertThat(scoutContacts.path("friends").size()).isEqualTo(1);
        assertThat(scoutContacts.path("friends").get(0).path("user").path("username").asText()).isEqualTo("captain");
        assertThat(scoutContacts.path("inboundPendingRequests").size()).isZero();
    }

    @Test
    void rejectsFriendRequest() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");

        JsonNode created = postJson(
            "/api/friend-requests",
            """
                {"userId":"%s","messageText":"Please add me"}
                """.formatted(scout.userId()),
            captain,
            201
        );

        postWithoutBody("/api/friend-requests/%s/reject".formatted(created.path("requestId").asText()), scout, 204);

        JsonNode captainContacts = getJson("/api/contacts", captain);
        JsonNode scoutContacts = getJson("/api/contacts", scout);
        assertThat(captainContacts.path("friends").size()).isZero();
        assertThat(captainContacts.path("outboundPendingRequests").size()).isZero();
        assertThat(scoutContacts.path("inboundPendingRequests").size()).isZero();
    }

    @Test
    void autoAcceptsOppositeDirectionRequestThroughCreateEndpoint() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");

        postJson(
            "/api/friend-requests",
            """
                {"username":"scout"}
                """,
            captain,
            201
        );

        JsonNode autoAccepted = postJson(
            "/api/friend-requests",
            """
                {"userId":"%s"}
                """.formatted(captain.userId()),
            scout,
            200
        );

        assertThat(autoAccepted.path("outcome").asText()).isEqualTo("AUTO_ACCEPTED");
        assertThat(autoAccepted.path("friendshipId").asText()).isNotBlank();
        assertThat(getJson("/api/contacts", captain).path("friends").size()).isEqualTo(1);
        assertThat(getJson("/api/contacts", captain).path("outboundPendingRequests").size()).isZero();
    }

    @Test
    void returnsContactsShapeWithAcceptedAndPendingRelationships() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");
        AuthenticatedClient pilot = registerAndLogin("pilot@example.com", "pilot");
        AuthenticatedClient analyst = registerAndLogin("analyst@example.com", "analyst");

        JsonNode captainToScout = postJson(
            "/api/friend-requests",
            """
                {"username":"scout"}
                """,
            captain,
            201
        );
        postWithoutBody("/api/friend-requests/%s/accept".formatted(captainToScout.path("requestId").asText()), scout, 204);

        postJson(
            "/api/friend-requests",
            """
                {"userId":"%s","messageText":"Inbound note"}
                """.formatted(captain.userId()),
            pilot,
            201
        );
        postJson(
            "/api/friend-requests",
            """
                {"username":"analyst","messageText":"Outbound note"}
                """,
            captain,
            201
        );

        JsonNode contacts = getJson("/api/contacts", captain);

        assertThat(contacts.path("friends").size()).isEqualTo(1);
        assertThat(contacts.path("friends").get(0).path("user").path("username").asText()).isEqualTo("scout");
        assertThat(contacts.path("inboundPendingRequests").size()).isEqualTo(1);
        assertThat(contacts.path("inboundPendingRequests").get(0).path("user").path("username").asText()).isEqualTo("pilot");
        assertThat(contacts.path("outboundPendingRequests").size()).isEqualTo(1);
        assertThat(contacts.path("outboundPendingRequests").get(0).path("user").path("username").asText()).isEqualTo("analyst");
        assertThat(contacts.path("blockedUsers").size()).isZero();
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
        MvcResult result = mockMvc.perform(get(path).cookie(client.sessionCookie()))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, String body, AuthenticatedClient client, int expectedStatus) throws Exception {
        return objectMapper.readTree(postJsonRaw(path, body, client.csrfCookie(), client.sessionCookie(), expectedStatus)
            .getResponse()
            .getContentAsString());
    }

    private void postWithoutBody(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
        postJsonRaw(path, null, client.csrfCookie(), client.sessionCookie(), expectedStatus);
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
