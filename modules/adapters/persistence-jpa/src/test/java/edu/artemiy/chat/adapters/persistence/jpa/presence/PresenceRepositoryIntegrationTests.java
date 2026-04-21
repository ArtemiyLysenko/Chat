package edu.artemiy.chat.adapters.persistence.jpa.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.adapters.persistence.jpa.PersistenceJpaTestApplication;
import edu.artemiy.chat.identity.spi.NewSessionRecord;
import edu.artemiy.chat.identity.spi.NewUserRecord;
import edu.artemiy.chat.identity.spi.SessionPersistencePort;
import edu.artemiy.chat.identity.spi.UserPersistencePort;
import edu.artemiy.chat.presence.spi.NewSessionTabRecord;
import edu.artemiy.chat.presence.spi.PresencePersistencePort;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(classes = PersistenceJpaTestApplication.class)
@ActiveProfiles("test")
class PresenceRepositoryIntegrationTests extends PostgresIntegrationSupport {

    private static final Instant NOW = Instant.parse("2026-04-21T12:00:00Z");

    @Autowired
    private PresencePersistencePort presencePersistencePort;

    @Autowired
    private SessionPersistencePort sessionPersistencePort;

    @Autowired
    private UserPersistencePort userPersistencePort;

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
        jdbcTemplate.execute(
            """
                truncate table
                    session_tabs,
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
    void upsertsTabPerSessionAndAllowsSameTabKeyAcrossDifferentSessions() {
        UUID userId = createUser("owner@example.com", "owner");
        UUID firstSessionId = createSession(userId, NOW, NOW.plus(30, ChronoUnit.DAYS));
        UUID secondSessionId = createSession(userId, NOW.plusSeconds(10), NOW.plus(30, ChronoUnit.DAYS));

        presencePersistencePort.registerTab(new NewSessionTabRecord(
            firstSessionId,
            "tab-1",
            NOW,
            NOW,
            NOW
        ));
        presencePersistencePort.registerTab(new NewSessionTabRecord(
            firstSessionId,
            "tab-1",
            NOW.plusSeconds(30),
            NOW.plusSeconds(20),
            NOW.plusSeconds(30)
        ));
        presencePersistencePort.registerTab(new NewSessionTabRecord(
            secondSessionId,
            "tab-1",
            NOW.plusSeconds(40),
            NOW.plusSeconds(40),
            NOW.plusSeconds(40)
        ));

        assertThat(jdbcTemplate.queryForObject("select count(*) from session_tabs", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select connected_at from session_tabs where session_id = ?::uuid and tab_key = 'tab-1'",
            Instant.class,
            firstSessionId
        )).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void listsOnlyOpenTabsFromActiveSessions() {
        UUID userId = createUser("member@example.com", "member");
        UUID activeSessionId = createSession(userId, NOW, NOW.plus(30, ChronoUnit.DAYS));
        UUID revokedSessionId = createSession(userId, NOW.minusSeconds(60), NOW.plus(30, ChronoUnit.DAYS));
        UUID expiredSessionId = createSession(userId, NOW.minusSeconds(120), NOW.minusSeconds(1));

        jdbcTemplate.update(
            "update user_sessions set revoked_at = ? where id = ?::uuid",
            Timestamp.from(NOW.minusSeconds(5)),
            revokedSessionId
        );

        presencePersistencePort.registerTab(new NewSessionTabRecord(
            activeSessionId,
            "active",
            NOW.minusSeconds(10),
            NOW.minusSeconds(10),
            NOW.minusSeconds(10)
        ));
        presencePersistencePort.registerTab(new NewSessionTabRecord(
            revokedSessionId,
            "revoked",
            NOW.minusSeconds(10),
            NOW.minusSeconds(10),
            NOW.minusSeconds(10)
        ));
        presencePersistencePort.registerTab(new NewSessionTabRecord(
            expiredSessionId,
            "expired",
            NOW.minusSeconds(10),
            NOW.minusSeconds(10),
            NOW.minusSeconds(10)
        ));
        presencePersistencePort.closeTab(activeSessionId, "active", NOW.minusSeconds(1));
        presencePersistencePort.registerTab(new NewSessionTabRecord(
            activeSessionId,
            "active-2",
            NOW.minusSeconds(2),
            NOW.minusSeconds(2),
            NOW.minusSeconds(2)
        ));

        var openTabs = presencePersistencePort.listOpenTabsByUserIds(List.of(userId), NOW);

        assertThat(openTabs).singleElement().satisfies(tab -> {
            assertThat(tab.userId()).isEqualTo(userId);
            assertThat(tab.sessionId()).isEqualTo(activeSessionId);
            assertThat(tab.tabKey()).isEqualTo("active-2");
        });
    }

    private UUID createUser(String email, String username) {
        return userPersistencePort.create(new NewUserRecord(
            UUID.randomUUID(),
            email,
            username,
            username,
            "hash",
            NOW
        )).id();
    }

    private UUID createSession(UUID userId, Instant createdAt, Instant expiresAt) {
        UUID sessionId = UUID.randomUUID();
        sessionPersistencePort.create(new NewSessionRecord(
            sessionId,
            userId,
            createdAt,
            createdAt,
            expiresAt,
            "TestBrowser/1.0",
            "127.0.0.1"
        ));
        return sessionId;
    }
}
