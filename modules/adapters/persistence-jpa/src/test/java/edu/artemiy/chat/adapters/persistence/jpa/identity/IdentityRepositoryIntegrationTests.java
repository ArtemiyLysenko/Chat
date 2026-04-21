package edu.artemiy.chat.adapters.persistence.jpa.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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
import edu.artemiy.chat.identity.spi.DuplicateUserIdentityException;
import edu.artemiy.chat.identity.spi.NewPasswordResetTokenRecord;
import edu.artemiy.chat.identity.spi.NewSessionRecord;
import edu.artemiy.chat.identity.spi.NewUserRecord;
import edu.artemiy.chat.identity.spi.PasswordResetTokenPersistencePort;
import edu.artemiy.chat.identity.spi.SessionPersistencePort;
import edu.artemiy.chat.identity.spi.UserPersistencePort;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(classes = PersistenceJpaTestApplication.class)
@ActiveProfiles("test")
class IdentityRepositoryIntegrationTests extends PostgresIntegrationSupport {

    private static final Instant NOW = Instant.parse("2026-04-20T12:00:00Z");

    @Autowired
    private UserPersistencePort userPersistencePort;

    @Autowired
    private SessionPersistencePort sessionPersistencePort;

    @Autowired
    private PasswordResetTokenPersistencePort passwordResetTokenPersistencePort;

    @Autowired
    private UserSessionJpaRepository userSessionJpaRepository;

    @Autowired
    private PasswordResetTokenJpaRepository passwordResetTokenJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

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
    void enforcesUniqueEmailAndUsername() {
        userPersistencePort.create(new NewUserRecord(
            UUID.randomUUID(),
            "captain@example.com",
            "captain",
            "captain",
            "hash",
            NOW
        ));

        assertThatThrownBy(() -> userPersistencePort.create(new NewUserRecord(
            UUID.randomUUID(),
            "CAPTAIN@example.com",
            "captain-two",
            "captain-two",
            "hash",
            NOW
        ))).isInstanceOfSatisfying(DuplicateUserIdentityException.class, exception ->
            assertThat(exception.conflictTarget()).isEqualTo(DuplicateUserIdentityException.ConflictTarget.EMAIL)
        );

        assertThatThrownBy(() -> userPersistencePort.create(new NewUserRecord(
            UUID.randomUUID(),
            "other@example.com",
            "CAPTAIN",
            "captain",
            "hash",
            NOW
        ))).isInstanceOfSatisfying(DuplicateUserIdentityException.class, exception ->
            assertThat(exception.conflictTarget()).isEqualTo(DuplicateUserIdentityException.ConflictTarget.USERNAME)
        );
    }

    @Test
    void resetTokensHonorExpiryAndOneTimeUse() {
        var user = userPersistencePort.create(new NewUserRecord(
            UUID.randomUUID(),
            "captain@example.com",
            "captain",
            "captain",
            "hash",
            NOW
        ));

        var activeToken = passwordResetTokenPersistencePort.create(new NewPasswordResetTokenRecord(
            UUID.randomUUID(),
            user.id(),
            "hash-1",
            NOW,
            NOW.plusSeconds(900)
        ));
        passwordResetTokenPersistencePort.create(new NewPasswordResetTokenRecord(
            UUID.randomUUID(),
            user.id(),
            "hash-2",
            NOW,
            NOW.minusSeconds(1)
        ));

        assertThat(passwordResetTokenPersistencePort.findUsableByTokenHash("hash-1", NOW)).isPresent();
        assertThat(passwordResetTokenPersistencePort.findUsableByTokenHash("hash-2", NOW)).isEmpty();

        assertThat(passwordResetTokenPersistencePort.markUsed(activeToken.id(), NOW.plusSeconds(1))).isTrue();
        assertThat(passwordResetTokenPersistencePort.markUsed(activeToken.id(), NOW.plusSeconds(2))).isFalse();

        assertThat(passwordResetTokenPersistencePort.findUsableByTokenHash("hash-1", NOW.plusSeconds(2))).isEmpty();
    }

    @Test
    void sessionQueriesRespectSelectiveRevocation() {
        var user = userPersistencePort.create(new NewUserRecord(
            UUID.randomUUID(),
            "captain@example.com",
            "captain",
            "captain",
            "hash",
            NOW
        ));

        var currentSession = sessionPersistencePort.create(new NewSessionRecord(
            UUID.randomUUID(),
            user.id(),
            NOW.minusSeconds(60),
            NOW.minusSeconds(60),
            NOW.plusSeconds(3600),
            "Firefox",
            "127.0.0.1"
        ));
        var otherSession = sessionPersistencePort.create(new NewSessionRecord(
            UUID.randomUUID(),
            user.id(),
            NOW.minusSeconds(120),
            NOW.minusSeconds(90),
            NOW.plusSeconds(3600),
            "Safari",
            "127.0.0.2"
        ));

        assertThat(sessionPersistencePort.findActiveByUserId(user.id(), NOW)).hasSize(2);

        assertThat(sessionPersistencePort.revokeSession(user.id(), otherSession.id(), NOW)).isTrue();
        assertThat(sessionPersistencePort.findActiveById(otherSession.id(), NOW)).isEmpty();
        assertThat(sessionPersistencePort.findActiveById(currentSession.id(), NOW)).isPresent();

        sessionPersistencePort.revokeAllOtherSessions(user.id(), currentSession.id(), NOW.plusSeconds(30));
        assertThat(sessionPersistencePort.findActiveByUserId(user.id(), NOW.plusSeconds(30)))
            .singleElement()
            .extracting(session -> session.id())
            .isEqualTo(currentSession.id());
    }
}
