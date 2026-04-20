package edu.artemiy.chat.app.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
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
class ApplicationHttpWiringTests extends PostgresIntegrationSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

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
        jdbcTemplate.execute("delete from user_sessions");
        jdbcTemplate.execute("delete from password_reset_tokens");
        jdbcTemplate.execute("delete from users");
    }

    @Test
    void enforcesCsrfAndIssuesSessionCookieOnLogin() throws Exception {
        BrowserSession browser = new BrowserSession();
        browser.get("/login");

        HttpResponse<String> forbiddenRegister = browser.postJson(
            "/api/auth/register",
            """
                {"email":"captain@example.com","username":"captain","password":"password123"}
                """,
            false
        );
        assertThat(forbiddenRegister.statusCode()).isEqualTo(403);

        HttpResponse<String> registered = browser.postJson(
            "/api/auth/register",
            """
                {"email":"captain@example.com","username":"captain","password":"password123"}
                """,
            true
        );
        assertThat(registered.statusCode()).isEqualTo(201);

        HttpResponse<String> login = browser.postJson(
            "/api/auth/login",
            """
                {"email":"captain@example.com","password":"password123"}
                """,
            true
        );

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie"))
            .anySatisfy(value -> {
                assertThat(value).contains("CHAT_SESSION=");
                assertThat(value).contains("HttpOnly");
                assertThat(value).contains("SameSite=Lax");
            });
        assertThat(browser.cookieValue("CHAT_SESSION")).isNotBlank();
        assertThat(browser.cookieValue("XSRF-TOKEN")).isNotBlank();
    }

    @Test
    void appliesRedirectRulesForProtectedAndAuthPages() throws Exception {
        BrowserSession browser = new BrowserSession();

        HttpResponse<String> unauthenticatedProtectedPage = browser.get("/app");
        assertThat(unauthenticatedProtectedPage.statusCode()).isEqualTo(302);
        assertThat(redirectPath(unauthenticatedProtectedPage)).isEqualTo("/login");

        HttpResponse<String> unauthenticatedProtectedStaticPage = browser.get("/app.html");
        assertThat(unauthenticatedProtectedStaticPage.statusCode()).isEqualTo(302);
        assertThat(redirectPath(unauthenticatedProtectedStaticPage)).isEqualTo("/login");

        HttpResponse<String> unauthenticatedSessionsStaticPage = browser.get("/sessions.html");
        assertThat(unauthenticatedSessionsStaticPage.statusCode()).isEqualTo(302);
        assertThat(redirectPath(unauthenticatedSessionsStaticPage)).isEqualTo("/login");

        registerAndLogin(browser, "captain@example.com", "captain");

        HttpResponse<String> authenticatedLoginPage = browser.get("/login");
        assertThat(authenticatedLoginPage.statusCode()).isEqualTo(302);
        assertThat(redirectPath(authenticatedLoginPage)).isEqualTo("/app");

        HttpResponse<String> authenticatedShell = browser.get("/app");
        assertThat(authenticatedShell.statusCode()).isEqualTo(200);
        assertThat(authenticatedShell.body()).contains("Password change");

        HttpResponse<String> authenticatedProtectedStaticPage = browser.get("/app.html");
        assertThat(authenticatedProtectedStaticPage.statusCode()).isEqualTo(200);
        assertThat(authenticatedProtectedStaticPage.body()).contains("Password change");
    }

    @Test
    void protectsSessionApiAndReturnsActiveSessions() throws Exception {
        BrowserSession firstBrowser = new BrowserSession();
        BrowserSession secondBrowser = new BrowserSession();

        registerAndLogin(firstBrowser, "captain@example.com", "captain");
        secondBrowser.get("/login");
        secondBrowser.postJson(
            "/api/auth/login",
            """
                {"email":"captain@example.com","password":"password123"}
                """,
            true
        );

        HttpResponse<String> unauthenticatedSessions = new BrowserSession().get("/api/sessions");
        assertThat(unauthenticatedSessions.statusCode()).isEqualTo(401);

        HttpResponse<String> sessions = firstBrowser.get("/api/sessions");
        assertThat(sessions.statusCode()).isEqualTo(200);
        assertThat(sessions.body()).contains("\"current\":true");
        assertThat(sessions.body()).contains("\"userAgent\":\"TestBrowser/1.0\"");
    }

    private void registerAndLogin(BrowserSession browser, String email, String username) throws Exception {
        browser.get("/register");
        browser.postJson(
            "/api/auth/register",
            """
                {"email":"%s","username":"%s","password":"password123"}
                """.formatted(email, username),
            true
        );
        browser.get("/login");
        browser.postJson(
            "/api/auth/login",
            """
                {"email":"%s","password":"password123"}
                """.formatted(email),
            true
        );
    }

    private static String redirectPath(HttpResponse<String> response) {
        return response.headers()
            .firstValue("Location")
            .map(URI::create)
            .map(URI::getPath)
            .orElse("");
    }

    private final class BrowserSession {

        private final CookieManager cookieManager = new CookieManager();
        private final HttpClient httpClient = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("User-Agent", "TestBrowser/1.0")
                .GET()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> postJson(String path, String body, boolean withCsrf) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("User-Agent", "TestBrowser/1.0");
            if (withCsrf) {
                builder.header("X-CSRF-TOKEN", cookieValue("XSRF-TOKEN"));
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        String cookieValue(String name) {
            Optional<HttpCookie> cookie = cookieManager.getCookieStore().getCookies().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst();
            return cookie.map(HttpCookie::getValue).orElse("");
        }

        private URI uri(String path) {
            return URI.create("http://localhost:" + port + path);
        }
    }
}
