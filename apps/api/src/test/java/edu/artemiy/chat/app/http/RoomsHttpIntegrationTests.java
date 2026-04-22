package edu.artemiy.chat.app.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.Filter;
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
import edu.artemiy.chat.presence.api.PresenceService;
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
class RoomsHttpIntegrationTests extends PostgresIntegrationSupport {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PresenceService presenceService;

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
                    direct_dialogs,
                    user_blocks,
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
    void publicCatalogReturnsVisiblePublicRoomsOnly() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient outsider = registerAndLogin("outsider@example.com", "outsider");

        createRoom(owner, "Lobby", "PUBLIC");
        createRoom(owner, "Private Deck", "PRIVATE");

        String catalog = getJson("/api/rooms?scope=catalog", outsider).getResponse().getContentAsString();

        assertThat(catalog).contains("Lobby");
        assertThat(catalog).doesNotContain("Private Deck");
    }

    @Test
    void joinedRoomScopeReturnsCurrentMemberships() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient member = registerAndLogin("member@example.com", "member");

        UUID roomId = createRoom(owner, "Lobby", "PUBLIC");
        postJson("/api/rooms/%s/join".formatted(roomId), null, member, 204);

        String joined = getJson("/api/rooms?scope=joined", member).getResponse().getContentAsString();

        assertThat(joined).contains("Lobby");
        assertThat(joined).contains("\"viewerRole\":\"MEMBER\"");
    }

    @Test
    void roomDetailsIncludeDerivedPresenceForMembers() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient member = registerAndLogin("member@example.com", "member");

        UUID roomId = createRoom(owner, "Bridge", "PUBLIC");
        postJson("/api/rooms/%s/join".formatted(roomId), null, member, 204);

        presenceService.registerTabConnection(member.userId(), member.sessionId(), "member-tab");

        JsonNode details = objectMapper.readTree(getJson("/api/rooms/" + roomId, owner).getResponse().getContentAsString());
        assertThat(details.path("members").findValuesAsText("presence")).contains("ONLINE");

        presenceService.closeTab(member.userId(), member.sessionId(), "member-tab");

        JsonNode closedDetails = objectMapper.readTree(getJson("/api/rooms/" + roomId, owner).getResponse().getContentAsString());
        assertThat(closedDetails.path("members").findValuesAsText("presence")).contains("OFFLINE");
    }

    @Test
    void privateRoomAccessRulesHideUnauthorizedUsersAndExposeInvitePreview() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient invitee = registerAndLogin("invitee@example.com", "invitee");
        AuthenticatedClient outsider = registerAndLogin("outsider@example.com", "outsider");

        UUID roomId = createRoom(owner, "Backstage", "PRIVATE");
        postJson(
            "/api/rooms/%s/invites".formatted(roomId),
            """
                {"userId":"%s"}
                """.formatted(invitee.userId()),
            owner,
            204
        );

        mockMvc.perform(get("/api/rooms/" + roomId).cookie(outsider.sessionCookie()))
            .andExpect(status().isNotFound());

        JsonNode preview = objectMapper.readTree(getJson("/api/rooms/" + roomId, invitee).getResponse().getContentAsString());

        assertThat(preview.path("accessLevel").asText()).isEqualTo("INVITED_PREVIEW");
        assertThat(preview.path("name").asText()).isEqualTo("Backstage");
        assertThat(preview.path("owner").path("id").asText()).isEqualTo(owner.userId().toString());
        assertThat(preview.path("description").isNull()).isTrue();
    }

    @Test
    void inviteBasedJoinFlowUsesJoinEndpointAndUnlocksFullDetails() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient invitee = registerAndLogin("invitee@example.com", "invitee");

        UUID roomId = createRoom(owner, "Backstage", "PRIVATE");
        postJson(
            "/api/rooms/%s/invites".formatted(roomId),
            """
                {"userId":"%s"}
                """.formatted(invitee.userId()),
            owner,
            204
        );

        postJson("/api/rooms/%s/join".formatted(roomId), null, invitee, 204);

        JsonNode details = objectMapper.readTree(getJson("/api/rooms/" + roomId, invitee).getResponse().getContentAsString());
        String joined = getJson("/api/rooms?scope=joined", invitee).getResponse().getContentAsString();

        assertThat(details.path("accessLevel").asText()).isEqualTo("FULL");
        assertThat(details.path("viewerRole").asText()).isEqualTo("MEMBER");
        assertThat(joined).contains("Backstage");
    }

    @Test
    void moderationActionsSupportRoleChangesRemovalBansAndUnbans() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient moderator = registerAndLogin("moderator@example.com", "moderator");
        AuthenticatedClient member = registerAndLogin("member@example.com", "member");

        UUID roomId = createRoom(owner, "Lobby", "PUBLIC");
        postJson("/api/rooms/%s/join".formatted(roomId), null, moderator, 204);
        postJson("/api/rooms/%s/join".formatted(roomId), null, member, 204);

        putJson("/api/rooms/%s/admins/%s".formatted(roomId, moderator.userId()), null, owner, 204);
        deleteJson("/api/rooms/%s/members/%s".formatted(roomId, member.userId()), null, owner, 204);

        String bansAfterMemberRemoval = getJson("/api/rooms/%s/bans".formatted(roomId), owner).getResponse().getContentAsString();
        assertThat(bansAfterMemberRemoval).doesNotContain(member.userId().toString());

        deleteJson("/api/rooms/%s/members/%s".formatted(roomId, moderator.userId()), null, owner, 204);

        String bans = getJson("/api/rooms/%s/bans".formatted(roomId), owner).getResponse().getContentAsString();
        assertThat(bans).contains(moderator.userId().toString()).contains(owner.userId().toString());

        deleteJson("/api/rooms/%s/bans/%s".formatted(roomId, moderator.userId()), null, owner, 204);
        String bansAfterUnban = getJson("/api/rooms/%s/bans".formatted(roomId), owner).getResponse().getContentAsString();
        assertThat(bansAfterUnban).doesNotContain(moderator.userId().toString());
    }

    @Test
    void roomDeletionRemovesRoomFromFutureReads() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient member = registerAndLogin("member@example.com", "member");

        UUID roomId = createRoom(owner, "Disposable", "PUBLIC");
        postJson("/api/rooms/%s/join".formatted(roomId), null, member, 204);

        deleteJson("/api/rooms/%s".formatted(roomId), null, owner, 204);

        mockMvc.perform(get("/api/rooms/" + roomId).cookie(member.sessionCookie()))
            .andExpect(status().isNotFound());
        assertThat(getJson("/api/rooms?scope=catalog", member).getResponse().getContentAsString()).doesNotContain("Disposable");
        assertThat(jdbcTemplate.queryForObject("select count(*) from rooms where id = ?", Integer.class, roomId)).isZero();
    }

    @Test
    void accountDeletionTriggersRoomsCleanup() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient host = registerAndLogin("host@example.com", "host");

        UUID ownedRoomId = createRoom(owner, "Owned Room", "PUBLIC");
        UUID hostRoomId = createRoom(host, "Host Room", "PUBLIC");
        UUID inviteRoomId = createRoom(host, "Invite Room", "PRIVATE");
        UUID bannedRoomId = createRoom(host, "Banned Room", "PUBLIC");

        postJson("/api/rooms/%s/join".formatted(hostRoomId), null, owner, 204);
        postJson(
            "/api/rooms/%s/invites".formatted(inviteRoomId),
            """
                {"userId":"%s"}
                """.formatted(owner.userId()),
            host,
            204
        );
        putJson(
            "/api/rooms/%s/bans/%s".formatted(bannedRoomId, owner.userId()),
            """
                {"reason":"Temporary"}
                """,
            host,
            204
        );

        deleteJson(
            "/api/account",
            """
                {"currentPassword":"password123"}
                """,
            owner,
            204
        );

        assertThat(jdbcTemplate.queryForObject("select count(*) from rooms where id = ?", Integer.class, ownedRoomId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_memberships where room_id = ? and user_id = ?",
            Integer.class,
            hostRoomId,
            owner.userId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_invites where room_id = ? and invited_user_id = ?",
            Integer.class,
            inviteRoomId,
            owner.userId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_bans where room_id = ? and user_id = ?",
            Integer.class,
            bannedRoomId,
            owner.userId()
        )).isZero();
    }

    private AuthenticatedClient registerAndLogin(String email, String username) throws Exception {
        Cookie csrfCookie = primeCsrfCookie();

        MockHttpServletResponse registerResponse = postJson(
            "/api/auth/register",
            """
                {"email":"%s","username":"%s","password":"password123"}
                """.formatted(email, username),
            csrfCookie,
            null,
            201
        ).getResponse();
        UUID userId = UUID.fromString(objectMapper.readTree(registerResponse.getContentAsString()).path("id").asText());

        MockHttpServletResponse loginResponse = postJson(
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

    private UUID createRoom(AuthenticatedClient client, String name, String visibility) throws Exception {
        MockHttpServletResponse response = postJson(
            "/api/rooms",
            """
                {"name":"%s","description":"%s description","visibility":"%s"}
                """.formatted(name, name, visibility),
            client.csrfCookie(),
            client.sessionCookie(),
            201
        ).getResponse();
        JsonNode created = objectMapper.readTree(response.getContentAsString());
        return UUID.fromString(created.path("id").asText());
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

    private MvcResult getJson(String path, AuthenticatedClient client) throws Exception {
        return mockMvc.perform(get(path).cookie(client.sessionCookie()))
            .andExpect(status().isOk())
            .andReturn();
    }

    private MvcResult postJson(String path, String body, AuthenticatedClient client, int expectedStatus) throws Exception {
        return postJson(path, body, client.csrfCookie(), client.sessionCookie(), expectedStatus);
    }

    private MvcResult postJson(String path, String body, Cookie csrfCookie, Cookie sessionCookie, int expectedStatus) throws Exception {
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

    private MvcResult putJson(String path, String body, AuthenticatedClient client, int expectedStatus) throws Exception {
        var request = put(path)
            .contentType(APPLICATION_JSON)
            .header("X-CSRF-TOKEN", client.csrfCookie().getValue())
            .cookie(client.csrfCookie(), client.sessionCookie());
        if (body != null) {
            request.content(body);
        }
        return mockMvc.perform(request)
            .andExpect(status().is(expectedStatus))
            .andReturn();
    }

    private MvcResult deleteJson(String path, String body, AuthenticatedClient client, int expectedStatus) throws Exception {
        var request = delete(path)
            .contentType(APPLICATION_JSON)
            .header("X-CSRF-TOKEN", client.csrfCookie().getValue())
            .cookie(client.csrfCookie(), client.sessionCookie());
        if (body != null) {
            request.content(body);
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

        UUID sessionId() {
            return UUID.fromString(sessionCookie.getValue());
        }
    }
}
