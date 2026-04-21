package edu.artemiy.chat.app.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
import org.springframework.mock.web.MockMultipartFile;
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
class AttachmentsHttpIntegrationTests extends PostgresIntegrationSupport {

    private static final Path STORAGE_ROOT = Path.of("build/tmp/attachments-http-tests").toAbsolutePath();

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
        registry.add("chat.storage-root", () -> STORAGE_ROOT.toString());
    }

    @BeforeEach
    void cleanDatabase() throws IOException {
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
                    message_attachments,
                    attachments,
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
        deleteRecursively(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
    }

    @Test
    void uploadsAttachmentExposesMetadataAndDownloadsBinary() throws Exception {
        AuthenticatedClient captain = registerAndLogin("captain@example.com", "captain");
        UUID roomId = createRoom(captain, "Uploads");
        byte[] content = "hello attachment".getBytes(StandardCharsets.UTF_8);

        JsonNode uploaded = uploadAttachment(
            "/api/chats/room/%s/attachments".formatted(roomId),
            new MockMultipartFile("file", "notes.txt", "text/plain", content),
            "Sprint notes",
            captain,
            201
        );

        JsonNode metadata = getJson("/api/attachments/%s".formatted(uploaded.path("id").asText()), captain, 200);
        JsonNode history = getJson("/api/chats/room/%s/messages".formatted(roomId), captain, 200);
        MvcResult download = getRaw("/api/attachments/%s/download".formatted(uploaded.path("id").asText()), captain, 200);

        assertThat(uploaded.path("originalName").asText()).isEqualTo("notes.txt");
        assertThat(uploaded.path("mediaType").asText()).isEqualTo("text/plain");
        assertThat(uploaded.path("sizeBytes").asInt()).isEqualTo(content.length);
        assertThat(metadata).isEqualTo(uploaded);
        assertThat(history.path("items")).hasSize(1);
        assertThat(history.path("items").get(0).path("bodyText").asText()).isEqualTo("Sprint notes");
        assertThat(history.path("items").get(0).path("attachments")).hasSize(1);
        assertThat(history.path("items").get(0).path("attachments").get(0).path("attachmentId").asText())
            .isEqualTo(uploaded.path("id").asText());
        assertThat(download.getResponse().getContentAsByteArray()).containsExactly(content);
        assertThat(download.getResponse().getContentType()).isEqualTo("text/plain");
        assertThat(download.getResponse().getHeader("Content-Disposition")).contains("notes.txt");
        assertThat(Files.readAllBytes(STORAGE_ROOT.resolve("uploads").resolve(uploaded.path("id").asText())))
            .containsExactly(content);
    }

    @Test
    void removedRoomMemberLosesMetadataAndDownloadAccessImmediately() throws Exception {
        AuthenticatedClient owner = registerAndLogin("owner@example.com", "owner");
        AuthenticatedClient member = registerAndLogin("member@example.com", "member");
        UUID roomId = createRoom(owner, "Moderated uploads");
        postWithoutBody("/api/rooms/%s/join".formatted(roomId), member, 204);

        JsonNode uploaded = uploadAttachment(
            "/api/chats/room/%s/attachments".formatted(roomId),
            new MockMultipartFile("file", "evidence.txt", "text/plain", "evidence".getBytes(StandardCharsets.UTF_8)),
            null,
            member,
            201
        );

        deleteWithoutBody("/api/rooms/%s/members/%s".formatted(roomId, member.userId()), owner, 204);

        getRaw("/api/attachments/%s".formatted(uploaded.path("id").asText()), member, 403);
        getRaw("/api/attachments/%s/download".formatted(uploaded.path("id").asText()), member, 403);
        assertThat(getJson("/api/attachments/%s".formatted(uploaded.path("id").asText()), owner, 200).path("id").asText())
            .isEqualTo(uploaded.path("id").asText());
    }

    @Test
    void bannedRoomMemberLosesMetadataAndDownloadAccessImmediately() throws Exception {
        AuthenticatedClient owner = registerAndLogin("ban-owner@example.com", "ban-owner");
        AuthenticatedClient member = registerAndLogin("ban-member@example.com", "ban-member");
        UUID roomId = createRoom(owner, "Banned uploads");
        postWithoutBody("/api/rooms/%s/join".formatted(roomId), member, 204);

        JsonNode uploaded = uploadAttachment(
            "/api/chats/room/%s/attachments".formatted(roomId),
            new MockMultipartFile("file", "ban-evidence.txt", "text/plain", "ban evidence".getBytes(StandardCharsets.UTF_8)),
            null,
            member,
            201
        );

        putWithoutBody("/api/rooms/%s/bans/%s".formatted(roomId, member.userId()), owner, 204);

        getRaw("/api/attachments/%s".formatted(uploaded.path("id").asText()), member, 403);
        getRaw("/api/attachments/%s/download".formatted(uploaded.path("id").asText()), member, 403);
        assertThat(getJson("/api/attachments/%s".formatted(uploaded.path("id").asText()), owner, 200).path("id").asText())
            .isEqualTo(uploaded.path("id").asText());
    }

    @Test
    void deletingRoomRemovesAttachmentMetadataAndFilesystemBlob() throws Exception {
        AuthenticatedClient owner = registerAndLogin("cleanup-owner@example.com", "cleanup-owner");
        UUID roomId = createRoom(owner, "Cleanup");
        byte[] content = "cleanup".getBytes(StandardCharsets.UTF_8);

        JsonNode uploaded = uploadAttachment(
            "/api/chats/room/%s/attachments".formatted(roomId),
            new MockMultipartFile("file", "cleanup.txt", "text/plain", content),
            null,
            owner,
            201
        );

        Path blobPath = STORAGE_ROOT.resolve("uploads").resolve(uploaded.path("id").asText());
        assertThat(Files.exists(blobPath)).isTrue();

        deleteWithoutBody("/api/rooms/%s".formatted(roomId), owner, 204);

        getRaw("/api/attachments/%s".formatted(uploaded.path("id").asText()), owner, 404);
        assertThat(Files.exists(blobPath)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from attachments where id = ?::uuid",
            Integer.class,
            uploaded.path("id").asText()
        )).isZero();
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

    private JsonNode uploadAttachment(
        String path,
        MockMultipartFile file,
        String commentText,
        AuthenticatedClient client,
        int expectedStatus
    ) throws Exception {
        var request = multipart(path)
            .file(file)
            .header("X-CSRF-TOKEN", client.csrfCookie().getValue())
            .cookie(client.csrfCookie(), client.sessionCookie());
        if (commentText != null) {
            request.param("commentText", commentText);
        }
        return objectMapper.readTree(mockMvc.perform(request)
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString());
    }

    private JsonNode getJson(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
        return objectMapper.readTree(getRaw(path, client, expectedStatus).getResponse().getContentAsString());
    }

    private MvcResult getRaw(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
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

    private void deleteWithoutBody(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
        var request = delete(path)
            .contentType(APPLICATION_JSON)
            .header("X-CSRF-TOKEN", client.csrfCookie().getValue())
            .cookie(client.csrfCookie(), client.sessionCookie());
        mockMvc.perform(request).andExpect(status().is(expectedStatus));
    }

    private void putWithoutBody(String path, AuthenticatedClient client, int expectedStatus) throws Exception {
        var request = put(path)
            .contentType(APPLICATION_JSON)
            .header("X-CSRF-TOKEN", client.csrfCookie().getValue())
            .cookie(client.csrfCookie(), client.sessionCookie());
        mockMvc.perform(request).andExpect(status().is(expectedStatus));
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

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(target -> {
                try {
                    Files.deleteIfExists(target);
                }
                catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private record AuthenticatedClient(UUID userId, Cookie sessionCookie, Cookie csrfCookie) {
    }
}
