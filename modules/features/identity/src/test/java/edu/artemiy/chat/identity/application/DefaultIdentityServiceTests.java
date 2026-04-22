package edu.artemiy.chat.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.artemiy.chat.identity.api.ChangePasswordCommand;
import edu.artemiy.chat.identity.api.ClientContext;
import edu.artemiy.chat.identity.api.ConsumePasswordResetCommand;
import edu.artemiy.chat.identity.api.DeleteAccountCommand;
import edu.artemiy.chat.identity.api.IdentityErrorType;
import edu.artemiy.chat.identity.api.IdentityException;
import edu.artemiy.chat.identity.api.IdentitySettings;
import edu.artemiy.chat.identity.api.LoginCommand;
import edu.artemiy.chat.identity.api.ResolvedUser;
import edu.artemiy.chat.identity.api.RegisterUserCommand;
import edu.artemiy.chat.identity.api.RequestPasswordResetCommand;
import edu.artemiy.chat.identity.api.UsernamePasswordAuthenticationCommand;
import edu.artemiy.chat.identity.domain.PasswordResetSecret;
import edu.artemiy.chat.identity.spi.AccountDeletionImpact;
import edu.artemiy.chat.identity.spi.AccountDeletionImpactPort;
import edu.artemiy.chat.identity.spi.AuthenticatedUser;
import edu.artemiy.chat.identity.spi.AuthenticatedUserPort;
import edu.artemiy.chat.identity.spi.DuplicateUserIdentityException;
import edu.artemiy.chat.identity.spi.NewPasswordResetTokenRecord;
import edu.artemiy.chat.identity.spi.NewSessionRecord;
import edu.artemiy.chat.identity.spi.NewUserRecord;
import edu.artemiy.chat.identity.spi.PasswordHasher;
import edu.artemiy.chat.identity.spi.PasswordResetNotification;
import edu.artemiy.chat.identity.spi.PasswordResetTokenPersistencePort;
import edu.artemiy.chat.identity.spi.ResetNotificationPort;
import edu.artemiy.chat.identity.spi.SessionPersistencePort;
import edu.artemiy.chat.identity.spi.StoredPasswordResetToken;
import edu.artemiy.chat.identity.spi.StoredSession;
import edu.artemiy.chat.identity.spi.StoredUser;
import edu.artemiy.chat.identity.spi.TombstoneUserRecord;
import edu.artemiy.chat.identity.spi.UserPersistencePort;
import edu.artemiy.chat.testing.FixedClock;

class DefaultIdentityServiceTests {

    private static final Instant NOW = Instant.parse("2026-04-20T12:00:00Z");

    private final FakeUserPersistencePort userPersistencePort = new FakeUserPersistencePort();
    private final FakeSessionPersistencePort sessionPersistencePort = new FakeSessionPersistencePort();
    private final FakePasswordResetTokenPersistencePort passwordResetTokenPersistencePort = new FakePasswordResetTokenPersistencePort();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final CapturingResetNotificationPort resetNotificationPort = new CapturingResetNotificationPort();
    private final MutableAuthenticatedUserPort authenticatedUserPort = new MutableAuthenticatedUserPort();
    private final CapturingAccountDeletionImpactPort accountDeletionImpactPort = new CapturingAccountDeletionImpactPort();

    private DefaultIdentityService service;

    @BeforeEach
    void setUp() {
        service = new DefaultIdentityService(
            new FixedClock(NOW),
            userPersistencePort,
            sessionPersistencePort,
            passwordResetTokenPersistencePort,
            passwordHasher,
            resetNotificationPort,
            authenticatedUserPort,
            accountDeletionImpactPort,
            new IdentitySettings(Duration.ofDays(30), Duration.ofMinutes(15)),
            event -> {
            }
        );
    }

    @Test
    void registerCreatesUserWithHashedPassword() {
        var registeredUser = service.register(new RegisterUserCommand("Person@example.com", "Captain_River", "password123"));

        StoredUser storedUser = userPersistencePort.findById(registeredUser.id()).orElseThrow();

        assertThat(registeredUser.email()).isEqualTo("person@example.com");
        assertThat(registeredUser.username()).isEqualTo("captain_river");
        assertThat(storedUser.passwordHash()).startsWith("hash::");
        assertThat(storedUser.displayName()).isEqualTo("captain_river");
    }

    @Test
    void loginCreatesPersistentSessionWithClientMetadata() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");

        var loginSession = service.login(new LoginCommand(
            "captain@example.com",
            "password123",
            new ClientContext("Firefox", "127.0.0.1")
        ));

        StoredSession storedSession = sessionPersistencePort.findActiveById(loginSession.sessionId(), NOW).orElseThrow();

        assertThat(storedSession.userId()).isEqualTo(user.id());
        assertThat(storedSession.userAgent()).isEqualTo("Firefox");
        assertThat(storedSession.ipAddress()).isEqualTo("127.0.0.1");
        assertThat(loginSession.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void logoutRevokesCurrentSessionOnly() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");
        StoredSession currentSession = createSession(user.id(), NOW);
        StoredSession otherSession = createSession(user.id(), NOW.minusSeconds(60));
        authenticatedUserPort.currentUser = Optional.of(new AuthenticatedUser(user.id(), currentSession.id()));

        service.logout();

        assertThat(sessionPersistencePort.findActiveById(currentSession.id(), NOW)).isEmpty();
        assertThat(sessionPersistencePort.findActiveById(otherSession.id(), NOW)).isPresent();
    }

    @Test
    void revokeSessionSupportsSelectiveRevocation() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");
        StoredSession currentSession = createSession(user.id(), NOW);
        StoredSession otherSession = createSession(user.id(), NOW.minusSeconds(60));
        authenticatedUserPort.currentUser = Optional.of(new AuthenticatedUser(user.id(), currentSession.id()));

        var result = service.revokeSession(otherSession.id().toString());

        assertThat(result.currentSessionRevoked()).isFalse();
        assertThat(sessionPersistencePort.findActiveById(otherSession.id(), NOW)).isEmpty();
        assertThat(sessionPersistencePort.findActiveById(currentSession.id(), NOW)).isPresent();
    }

    @Test
    void changePasswordKeepsCurrentSessionAndRevokesOthers() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");
        StoredSession currentSession = createSession(user.id(), NOW);
        StoredSession otherSession = createSession(user.id(), NOW.minusSeconds(60));
        authenticatedUserPort.currentUser = Optional.of(new AuthenticatedUser(user.id(), currentSession.id()));

        service.changePassword(new ChangePasswordCommand("password123", "password456"));

        assertThat(passwordHasher.matches("password456", userPersistencePort.findById(user.id()).orElseThrow().passwordHash())).isTrue();
        assertThat(sessionPersistencePort.findActiveById(currentSession.id(), NOW)).isPresent();
        assertThat(sessionPersistencePort.findActiveById(otherSession.id(), NOW)).isEmpty();
    }

    @Test
    void requestAndConsumeResetTokenRevokesAllSessions() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");
        createSession(user.id(), NOW);
        createSession(user.id(), NOW.minusSeconds(90));

        service.requestPasswordReset(new RequestPasswordResetCommand("captain@example.com", "http://localhost:8080"));
        String rawToken = resetNotificationPort.lastNotification.rawToken();

        service.resetPassword(new ConsumePasswordResetCommand(rawToken, "password789"));

        assertThat(passwordHasher.matches("password789", userPersistencePort.findById(user.id()).orElseThrow().passwordHash())).isTrue();
        assertThat(sessionPersistencePort.findActiveByUserId(user.id(), NOW)).isEmpty();
        assertThat(passwordResetTokenPersistencePort.findUsableByTokenHash(PasswordResetSecret.hash(rawToken), NOW)).isEmpty();
    }

    @Test
    void resetPasswordRejectsTokenWhenAnotherRequestAlreadyConsumedIt() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");
        createSession(user.id(), NOW);

        service.requestPasswordReset(new RequestPasswordResetCommand("captain@example.com", "http://localhost:8080"));
        String rawToken = resetNotificationPort.lastNotification.rawToken();
        passwordResetTokenPersistencePort.rejectMarkUsed = true;

        assertThatThrownBy(() -> service.resetPassword(new ConsumePasswordResetCommand(rawToken, "password789")))
            .isInstanceOfSatisfying(IdentityException.class, exception -> {
                assertThat(exception.code()).isEqualTo("identity.reset_token_invalid");
                assertThat(exception.errorType()).isEqualTo(IdentityErrorType.BAD_REQUEST);
            });

        assertThat(passwordHasher.matches("password123", userPersistencePort.findById(user.id()).orElseThrow().passwordHash())).isTrue();
        assertThat(sessionPersistencePort.findActiveByUserId(user.id(), NOW)).hasSize(1);
        assertThat(passwordResetTokenPersistencePort.findUsableByTokenHash(PasswordResetSecret.hash(rawToken), NOW)).isPresent();
    }

    @Test
    void registerTranslatesConcurrentDuplicateIdentityConflict() {
        userPersistencePort.duplicateOnCreate = DuplicateUserIdentityException.ConflictTarget.EMAIL;

        assertThatThrownBy(() -> service.register(new RegisterUserCommand("captain@example.com", "captain", "password123")))
            .isInstanceOfSatisfying(IdentityException.class, exception -> {
                assertThat(exception.code()).isEqualTo("identity.email_taken");
                assertThat(exception.errorType()).isEqualTo(IdentityErrorType.CONFLICT);
            });
    }

    @Test
    void deleteAccountTombstonesIdentityAndInvokesCleanupHook() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");
        StoredSession currentSession = createSession(user.id(), NOW);
        createSession(user.id(), NOW.minusSeconds(120));
        authenticatedUserPort.currentUser = Optional.of(new AuthenticatedUser(user.id(), currentSession.id()));

        service.deleteAccount(new DeleteAccountCommand("password123"));

        StoredUser tombstonedUser = userPersistencePort.findById(user.id()).orElseThrow();
        assertThat(tombstonedUser.passwordHash()).isNull();
        assertThat(tombstonedUser.deletedAt()).isEqualTo(NOW);
        assertThat(tombstonedUser.displayName()).isEqualTo("Deleted user");
        assertThat(tombstonedUser.email()).startsWith("deleted-");
        assertThat(sessionPersistencePort.findActiveByUserId(user.id(), NOW)).isEmpty();
        assertThat(accountDeletionImpactPort.deletedUserIds).containsExactly(user.id());
    }

    @Test
    void rejectsInvalidCredentials() {
        createUser("captain@example.com", "captain", "password123");

        assertThatThrownBy(() -> service.login(new LoginCommand(
            "captain@example.com",
            "wrong-password",
            new ClientContext("Firefox", "127.0.0.1")
        )))
            .isInstanceOf(IdentityException.class)
            .hasMessageContaining("incorrect");
    }

    @Test
    void authenticatesByUsernameForXmppStyleLogin() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");

        ResolvedUser resolvedUser = service.authenticateByUsernamePassword(
            new UsernamePasswordAuthenticationCommand("Captain", "password123")
        );

        assertThat(resolvedUser.userId()).isEqualTo(user.id());
        assertThat(resolvedUser.username()).isEqualTo("captain");
    }

    @Test
    void usernameAuthenticationRejectsTombstonedUsers() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");
        userPersistencePort.tombstone(new TombstoneUserRecord(
            user.id(),
            "deleted@example.com",
            "deleted-user",
            "Deleted user",
            NOW
        ));

        assertThatThrownBy(() -> service.authenticateByUsernamePassword(
            new UsernamePasswordAuthenticationCommand("captain", "password123")
        ))
            .isInstanceOf(IdentityException.class)
            .hasMessageContaining("incorrect");
    }

    @Test
    void findsActiveUserByUsernameIgnoringCase() {
        StoredUser user = createUser("captain@example.com", "captain", "password123");

        assertThat(service.findActiveUserByUsername("Captain"))
            .contains(new ResolvedUser(user.id(), "captain", "captain"));
    }

    private StoredUser createUser(String email, String username, String rawPassword) {
        return userPersistencePort.create(new NewUserRecord(
            UUID.randomUUID(),
            email,
            username,
            username,
            passwordHasher.hash(rawPassword),
            NOW
        ));
    }

    private StoredSession createSession(UUID userId, Instant createdAt) {
        return sessionPersistencePort.create(new NewSessionRecord(
            UUID.randomUUID(),
            userId,
            createdAt,
            createdAt,
            createdAt.plus(Duration.ofDays(30)),
            "Firefox",
            "127.0.0.1"
        ));
    }

    private static final class FakeUserPersistencePort implements UserPersistencePort {

        private final Map<UUID, StoredUser> users = new LinkedHashMap<>();
        private DuplicateUserIdentityException.ConflictTarget duplicateOnCreate;

        @Override
        public boolean existsByEmail(String email) {
            return users.values().stream().anyMatch(user -> user.email().equalsIgnoreCase(email));
        }

        @Override
        public boolean existsByUsername(String username) {
            return users.values().stream().anyMatch(user -> user.username().equalsIgnoreCase(username));
        }

        @Override
        public Optional<StoredUser> findByEmail(String email) {
            return users.values().stream().filter(user -> user.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public Optional<StoredUser> findByUsername(String username) {
            return users.values().stream().filter(user -> user.username().equalsIgnoreCase(username)).findFirst();
        }

        @Override
        public Optional<StoredUser> findById(UUID userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public StoredUser create(NewUserRecord newUser) {
            if (duplicateOnCreate != null) {
                DuplicateUserIdentityException.ConflictTarget conflictTarget = duplicateOnCreate;
                duplicateOnCreate = null;
                throw new DuplicateUserIdentityException(conflictTarget, null);
            }
            var storedUser = new StoredUser(
                newUser.id(),
                newUser.email(),
                newUser.username(),
                newUser.displayName(),
                newUser.passwordHash(),
                newUser.createdAt(),
                null
            );
            users.put(storedUser.id(), storedUser);
            return storedUser;
        }

        @Override
        public void updatePassword(UUID userId, String passwordHash) {
            StoredUser existing = users.get(userId);
            users.put(userId, new StoredUser(
                existing.id(),
                existing.email(),
                existing.username(),
                existing.displayName(),
                passwordHash,
                existing.createdAt(),
                existing.deletedAt()
            ));
        }

        @Override
        public void tombstone(TombstoneUserRecord tombstoneUser) {
            StoredUser existing = users.get(tombstoneUser.userId());
            users.put(tombstoneUser.userId(), new StoredUser(
                existing.id(),
                tombstoneUser.email(),
                tombstoneUser.username(),
                tombstoneUser.displayName(),
                null,
                existing.createdAt(),
                tombstoneUser.deletedAt()
            ));
        }
    }

    private static final class FakeSessionPersistencePort implements SessionPersistencePort {

        private final Map<UUID, StoredSession> sessions = new LinkedHashMap<>();

        @Override
        public StoredSession create(NewSessionRecord newSession) {
            StoredSession storedSession = new StoredSession(
                newSession.id(),
                newSession.userId(),
                newSession.createdAt(),
                newSession.lastSeenAt(),
                newSession.expiresAt(),
                null,
                newSession.userAgent(),
                newSession.ipAddress()
            );
            sessions.put(storedSession.id(), storedSession);
            return storedSession;
        }

        @Override
        public Optional<StoredSession> findActiveById(UUID sessionId, Instant now) {
            return Optional.ofNullable(sessions.get(sessionId))
                .filter(session -> session.revokedAt() == null && session.expiresAt().isAfter(now));
        }

        @Override
        public List<StoredSession> findActiveByUserId(UUID userId, Instant now) {
            return sessions.values().stream()
                .filter(session -> session.userId().equals(userId))
                .filter(session -> session.revokedAt() == null && session.expiresAt().isAfter(now))
                .sorted(Comparator.comparing(StoredSession::lastSeenAt).reversed())
                .toList();
        }

        @Override
        public void touch(UUID sessionId, Instant lastSeenAt, String userAgent, String ipAddress) {
            StoredSession existing = sessions.get(sessionId);
            sessions.put(sessionId, new StoredSession(
                existing.id(),
                existing.userId(),
                existing.createdAt(),
                lastSeenAt,
                existing.expiresAt(),
                existing.revokedAt(),
                userAgent,
                ipAddress
            ));
        }

        @Override
        public boolean revokeSession(UUID userId, UUID sessionId, Instant revokedAt) {
            StoredSession existing = sessions.get(sessionId);
            if (existing == null || !existing.userId().equals(userId) || existing.revokedAt() != null || !existing.expiresAt().isAfter(revokedAt)) {
                return false;
            }
            sessions.put(sessionId, new StoredSession(
                existing.id(),
                existing.userId(),
                existing.createdAt(),
                existing.lastSeenAt(),
                existing.expiresAt(),
                revokedAt,
                existing.userAgent(),
                existing.ipAddress()
            ));
            return true;
        }

        @Override
        public List<UUID> revokeAllSessions(UUID userId, Instant revokedAt) {
            List<UUID> revokedSessionIds = new ArrayList<>(sessions.values()).stream()
                .filter(session -> session.userId().equals(userId))
                .map(StoredSession::id)
                .toList();
            revokedSessionIds.forEach(sessionId -> revokeSession(userId, sessionId, revokedAt));
            return revokedSessionIds;
        }

        @Override
        public List<UUID> revokeAllOtherSessions(UUID userId, UUID currentSessionId, Instant revokedAt) {
            List<UUID> revokedSessionIds = new ArrayList<>(sessions.values()).stream()
                .filter(session -> session.userId().equals(userId))
                .filter(session -> !session.id().equals(currentSessionId))
                .map(StoredSession::id)
                .toList();
            revokedSessionIds.forEach(sessionId -> revokeSession(userId, sessionId, revokedAt));
            return revokedSessionIds;
        }
    }

    private static final class FakePasswordResetTokenPersistencePort implements PasswordResetTokenPersistencePort {

        private final Map<UUID, StoredPasswordResetToken> tokens = new LinkedHashMap<>();
        private boolean rejectMarkUsed;

        @Override
        public StoredPasswordResetToken create(NewPasswordResetTokenRecord newToken) {
            StoredPasswordResetToken storedToken = new StoredPasswordResetToken(
                newToken.id(),
                newToken.userId(),
                newToken.tokenHash(),
                newToken.createdAt(),
                newToken.expiresAt(),
                null
            );
            tokens.put(storedToken.id(), storedToken);
            return storedToken;
        }

        @Override
        public Optional<StoredPasswordResetToken> findUsableByTokenHash(String tokenHash, Instant now) {
            return tokens.values().stream()
                .filter(token -> token.tokenHash().equals(tokenHash))
                .filter(token -> token.usedAt() == null)
                .filter(token -> token.expiresAt().isAfter(now))
                .findFirst();
        }

        @Override
        public boolean markUsed(UUID tokenId, Instant usedAt) {
            if (rejectMarkUsed) {
                return false;
            }
            StoredPasswordResetToken existing = tokens.get(tokenId);
            tokens.put(tokenId, new StoredPasswordResetToken(
                existing.id(),
                existing.userId(),
                existing.tokenHash(),
                existing.createdAt(),
                existing.expiresAt(),
                usedAt
            ));
            return true;
        }

        @Override
        public void invalidateOutstandingTokens(UUID userId, Instant invalidatedAt) {
            new ArrayList<>(tokens.values()).stream()
                .filter(token -> token.userId().equals(userId))
                .filter(token -> token.usedAt() == null)
                .filter(token -> token.expiresAt().isAfter(invalidatedAt))
                .forEach(token -> markUsed(token.id(), invalidatedAt));
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {

        @Override
        public String hash(String rawPassword) {
            return "hash::" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash != null && passwordHash.equals(hash(rawPassword));
        }
    }

    private static final class CapturingResetNotificationPort implements ResetNotificationPort {

        private PasswordResetNotification lastNotification;

        @Override
        public void sendPasswordReset(PasswordResetNotification notification) {
            lastNotification = notification;
        }
    }

    private static final class MutableAuthenticatedUserPort implements AuthenticatedUserPort {

        private Optional<AuthenticatedUser> currentUser = Optional.empty();

        @Override
        public Optional<AuthenticatedUser> currentUser() {
            return currentUser;
        }
    }

    private static final class CapturingAccountDeletionImpactPort implements AccountDeletionImpactPort {

        private final List<UUID> deletedUserIds = new ArrayList<>();

        @Override
        public void handleAccountDeleted(AccountDeletionImpact impact) {
            deletedUserIds.add(impact.userId());
        }
    }
}
