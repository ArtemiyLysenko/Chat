package edu.artemiy.chat.app.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void postRoomMessageSupportsReplyTarget() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Bridge");
        JsonNode parent = postJson(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":"Hello room"}
                """,
            captain,
            201
        );

        JsonNode reply = postJson(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":"Replying","parentMessageId":"%s"}
                """.formatted(parent.path("id").asText()),
            captain,
            201
        );

        assertThat(reply.path("state").asText()).isEqualTo("ACTIVE");
        assertThat(reply.path("replyTo").path("messageId").asText()).isEqualTo(parent.path("id").asText());
        assertThat(reply.path("replyTo").path("bodyText").asText()).isEqualTo("Hello room");
    }

    @Test
    void patchMessageEditsOwnMessage() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Bridge");
        JsonNode created = postJson(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":"Draft"}
                """,
            captain,
            201
        );

        JsonNode edited = patchJson(
            "/api/messages/%s".formatted(created.path("id").asText()),
            """
                {"bodyText":"Edited"}
                """,
            captain,
            200
        );

        assertThat(edited.path("state").asText()).isEqualTo("EDITED");
        assertThat(edited.path("bodyText").asText()).isEqualTo("Edited");
        assertThat(edited.path("editedAt").asText()).isNotBlank();
    }

    @Test
    void deleteMessageMarksItDeleted() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Bridge");
        JsonNode created = postJson(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":"Remove me"}
                """,
            captain,
            201
        );

        JsonNode deleted = deleteJson(
            "/api/messages/%s".formatted(created.path("id").asText()),
            captain,
            200
        );

        assertThat(deleted.path("state").asText()).isEqualTo("DELETED");
        assertThat(deleted.path("bodyText").asText()).isEmpty();
    }

    @Test
    void roomModeratorCanDeleteOtherAuthorsMessage() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient moderator = registerAndLogin("moderator@example.com", "moderator");
        AuthenticatedClient member = registerAndLogin("member@example.com", "member");

        UUID roomId = createRoom(owner, "Bridge");
        postWithoutBody("/api/rooms/%s/join".formatted(roomId), moderator, 204);
        postWithoutBody("/api/rooms/%s/join".formatted(roomId), member, 204);
        putWithoutBody("/api/rooms/%s/admins/%s".formatted(roomId, moderator.userId()), owner, 204);

        JsonNode created = postJson(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":"Moderate me"}
                """,
            member,
            201
        );

        JsonNode deleted = deleteJson(
            "/api/messages/%s".formatted(created.path("id").asText()),
            moderator,
            200
        );

        assertThat(deleted.path("state").asText()).isEqualTo("DELETED");
    }

    @Test
    void directDialogDeleteIsAuthorOnly() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");
        makeFriends(captain, scout);
        JsonNode dialog = postJson("/api/direct-dialogs/%s".formatted(scout.userId()), null, captain, 201);
        UUID directDialogId = UUID.fromString(dialog.path("dialogId").asText());

        JsonNode created = postJson(
            "/api/chats/direct/%s/messages".formatted(directDialogId),
            """
                {"bodyText":"Mine"}
                """,
            captain,
            201
        );

        MockHttpServletResponse deniedDelete = deleteJsonRaw(
            "/api/messages/%s".formatted(created.path("id").asText()),
            scout.csrfCookie(),
            scout.sessionCookie(),
            403
        ).getResponse();
        JsonNode deleted = deleteJson(
            "/api/messages/%s".formatted(created.path("id").asText()),
            captain,
            200
        );

        assertThat(objectMapper.readTree(deniedDelete.getContentAsString()).path("code").asText())
            .isEqualTo("messaging.message_delete_forbidden");
        assertThat(deleted.path("state").asText()).isEqualTo("DELETED");
    }

    @Test
    void getMessagesReturnsChronologicalPagesAndSupportsBeforeCursorWithEditedDeletedAndReplyMessages() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Bridge");

        JsonNode first = postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"One\"}", captain, 201);
        JsonNode second = postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"Two\"}", captain, 201);
        patchJson("/api/messages/%s".formatted(second.path("id").asText()), "{\"bodyText\":\"Two edited\"}", captain, 200);
        JsonNode third = postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"Three\"}", captain, 201);
        deleteJson("/api/messages/%s".formatted(third.path("id").asText()), captain, 200);
        postJson(
            "/api/chats/room/%s/messages".formatted(roomId),
            """
                {"bodyText":"Four","parentMessageId":"%s"}
                """.formatted(first.path("id").asText()),
            captain,
            201
        );

        JsonNode page = getJson("/api/chats/room/%s/messages?limit=4".formatted(roomId), captain);

        assertThat(page.path("items").size()).isEqualTo(4);
        assertThat(page.path("items").get(0).path("bodyText").asText()).isEqualTo("One");
        assertThat(page.path("items").get(1).path("state").asText()).isEqualTo("EDITED");
        assertThat(page.path("items").get(2).path("state").asText()).isEqualTo("DELETED");
        assertThat(page.path("items").get(2).path("bodyText").asText()).isEmpty();
        assertThat(page.path("items").get(3).path("replyTo").path("messageId").asText()).isEqualTo(first.path("id").asText());
    }

    @Test
    void roomUnreadBadgeCountsClearThroughReadMarkers() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");
        UUID roomId = createRoom(owner, "Bridge");
        postWithoutBody("/api/rooms/%s/join".formatted(roomId), scout, 204);

        JsonNode first = postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"One\"}", owner, 201);
        JsonNode second = postJson("/api/chats/room/%s/messages".formatted(roomId), "{\"bodyText\":\"Two\"}", owner, 201);

        JsonNode beforeRead = getJson("/api/rooms?scope=joined", scout);
        postJson(
            "/api/chats/room/%s/read-markers".formatted(roomId),
            """
                {"lastReadMessageId":"%s"}
                """.formatted(second.path("id").asText()),
            scout,
            200
        );
        JsonNode afterRead = getJson("/api/rooms?scope=joined", scout);

        assertThat(beforeRead.get(0).path("unreadCount").asInt()).isEqualTo(2);
        assertThat(afterRead.get(0).path("unreadCount").asInt()).isZero();
        assertThat(first.path("id").asText()).isNotBlank();
    }

    @Test
    void directDialogUnreadBadgeCountsClearThroughReadMarkers() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        AuthenticatedClient scout = registerAndLogin("scout@example.com", "scout");
        makeFriends(captain, scout);
        JsonNode dialog = postJson("/api/direct-dialogs/%s".formatted(scout.userId()), null, captain, 201);
        UUID directDialogId = UUID.fromString(dialog.path("dialogId").asText());

        JsonNode message = postJson(
            "/api/chats/direct/%s/messages".formatted(directDialogId),
            """
                {"bodyText":"Hello scout"}
                """,
            captain,
            201
        );

        JsonNode beforeRead = getJson("/api/contacts", scout);
        postJson(
            "/api/chats/direct/%s/read-markers".formatted(directDialogId),
            """
                {"lastReadMessageId":"%s"}
                """.formatted(message.path("id").asText()),
            scout,
            200
        );
        JsonNode afterRead = getJson("/api/contacts", scout);

        assertThat(beforeRead.path("viewerUserId").asText()).isEqualTo(scout.userId().toString());
        assertThat(beforeRead.path("friends").get(0).path("directDialogId").asText()).isEqualTo(directDialogId.toString());
        assertThat(beforeRead.path("friends").get(0).path("unreadCount").asInt()).isEqualTo(1);
        assertThat(afterRead.path("friends").get(0).path("unreadCount").asInt()).isZero();
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

    private JsonNode patchJson(String path, String body, AuthenticatedClient client, int expectedStatus) throws Exception {
        return objectMapper.readTree(patchJsonRaw(path, body, client.csrfCookie(), client.sessionCookie(), expectedStatus)
            .getResponse()
            .getContentAsString());
    }

    private JsonNode deleteJson(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
        return objectMapper.readTree(deleteJsonRaw(path, client.csrfCookie(), client.sessionCookie(), expectedStatus)
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

    private MvcResult patchJsonRaw(String path, String body, Cookie csrfCookie, Cookie sessionCookie, int expectedStatus) throws Exception {
        var request = patch(path)
            .contentType(APPLICATION_JSON)
            .header("X-CSRF-TOKEN", csrfCookie.getValue())
            .cookie(csrfCookie);
        if (body != null) {
            request.content(body);
        }
        if (sessionCookie != null) {
            request.cookie(sessionCookie);
        }
        return mockMvc.perform(request)
            .andExpect(status().is(expectedStatus))
            .andReturn();
    }

    private MvcResult deleteJsonRaw(String path, Cookie csrfCookie, Cookie sessionCookie, int expectedStatus) throws Exception {
        var request = delete(path)
            .contentType(APPLICATION_JSON)
            .header("X-CSRF-TOKEN", csrfCookie.getValue())
            .cookie(csrfCookie);
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
