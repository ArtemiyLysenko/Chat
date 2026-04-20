package edu.artemiy.chat.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.identity.api.AuthenticatedSession;
import edu.artemiy.chat.identity.api.ChangePasswordCommand;
import edu.artemiy.chat.identity.api.ClientContext;
import edu.artemiy.chat.identity.api.ConsumePasswordResetCommand;
import edu.artemiy.chat.identity.api.DeleteAccountCommand;
import edu.artemiy.chat.identity.api.IdentityErrorType;
import edu.artemiy.chat.identity.api.IdentityException;
import edu.artemiy.chat.identity.api.IdentityService;
import edu.artemiy.chat.identity.api.IdentitySettings;
import edu.artemiy.chat.identity.api.LoginCommand;
import edu.artemiy.chat.identity.api.LoginSession;
import edu.artemiy.chat.identity.api.RegisterUserCommand;
import edu.artemiy.chat.identity.api.RegisteredUser;
import edu.artemiy.chat.identity.api.RequestPasswordResetCommand;
import edu.artemiy.chat.identity.api.SessionRevocationResult;
import edu.artemiy.chat.identity.api.SessionSummary;
import edu.artemiy.chat.identity.domain.CredentialRules;
import edu.artemiy.chat.identity.domain.PasswordResetSecret;
import edu.artemiy.chat.identity.domain.TombstoneIdentity;
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

@Service
public class DefaultIdentityService implements IdentityService {

    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final int MAX_IP_LENGTH = 64;

    private final ClockPort clockPort;
    private final UserPersistencePort userPersistencePort;
    private final SessionPersistencePort sessionPersistencePort;
    private final PasswordResetTokenPersistencePort passwordResetTokenPersistencePort;
    private final PasswordHasher passwordHasher;
    private final ResetNotificationPort resetNotificationPort;
    private final AuthenticatedUserPort authenticatedUserPort;
    private final AccountDeletionImpactPort accountDeletionImpactPort;
    private final IdentitySettings identitySettings;

    public DefaultIdentityService(
        ClockPort clockPort,
        UserPersistencePort userPersistencePort,
        SessionPersistencePort sessionPersistencePort,
        PasswordResetTokenPersistencePort passwordResetTokenPersistencePort,
        PasswordHasher passwordHasher,
        ResetNotificationPort resetNotificationPort,
        AuthenticatedUserPort authenticatedUserPort,
        AccountDeletionImpactPort accountDeletionImpactPort,
        IdentitySettings identitySettings
    ) {
        this.clockPort = clockPort;
        this.userPersistencePort = userPersistencePort;
        this.sessionPersistencePort = sessionPersistencePort;
        this.passwordResetTokenPersistencePort = passwordResetTokenPersistencePort;
        this.passwordHasher = passwordHasher;
        this.resetNotificationPort = resetNotificationPort;
        this.authenticatedUserPort = authenticatedUserPort;
        this.accountDeletionImpactPort = accountDeletionImpactPort;
        this.identitySettings = identitySettings;
    }

    @Override
    @Transactional
    public RegisteredUser register(RegisterUserCommand command) {
        String email = CredentialRules.normalizeEmail(command.email());
        String username = CredentialRules.normalizeUsername(command.username());
        CredentialRules.validatePassword(command.password());

        if (userPersistencePort.existsByEmail(email)) {
            throw new IdentityException("identity.email_taken", "Email is already registered.", IdentityErrorType.CONFLICT);
        }
        if (userPersistencePort.existsByUsername(username)) {
            throw new IdentityException("identity.username_taken", "Username is already registered.", IdentityErrorType.CONFLICT);
        }

        Instant now = clockPort.now();
        StoredUser storedUser;
        try {
            storedUser = userPersistencePort.create(new NewUserRecord(
                UUID.randomUUID(),
                email,
                username,
                username,
                passwordHasher.hash(command.password()),
                now
            ));
        }
        catch (DuplicateUserIdentityException exception) {
            throw duplicateRegistrationConflict(exception);
        }

        return new RegisteredUser(
            storedUser.id(),
            storedUser.email(),
            storedUser.username(),
            storedUser.displayName(),
            storedUser.createdAt()
        );
    }

    @Override
    @Transactional
    public LoginSession login(LoginCommand command) {
        String email = CredentialRules.normalizeEmail(command.email());
        StoredUser user = userPersistencePort.findByEmail(email)
            .filter(candidate -> !candidate.deleted())
            .orElseThrow(this::invalidCredentials);

        if (user.passwordHash() == null || !passwordHasher.matches(command.password(), user.passwordHash())) {
            throw invalidCredentials();
        }

        Instant now = clockPort.now();
        Instant expiresAt = now.plus(identitySettings.sessionTtl());
        ClientContext clientContext = defaultContext(command.clientContext());
        StoredSession session = sessionPersistencePort.create(new NewSessionRecord(
            UUID.randomUUID(),
            user.id(),
            now,
            now,
            expiresAt,
            truncate(clientContext.userAgent(), MAX_USER_AGENT_LENGTH),
            truncate(clientContext.ipAddress(), MAX_IP_LENGTH)
        ));

        return new LoginSession(session.id(), session.expiresAt(), user.username(), user.displayName());
    }

    @Override
    @Transactional
    public void logout() {
        AuthenticatedUser authenticatedUser = requiredAuthenticatedUser();
        sessionPersistencePort.revokeSession(authenticatedUser.userId(), authenticatedUser.sessionId(), clockPort.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionSummary> listSessions() {
        AuthenticatedUser authenticatedUser = requiredAuthenticatedUser();
        Instant now = clockPort.now();
        return sessionPersistencePort.findActiveByUserId(authenticatedUser.userId(), now).stream()
            .map(session -> new SessionSummary(
                session.id(),
                session.id().equals(authenticatedUser.sessionId()),
                session.createdAt(),
                session.lastSeenAt(),
                session.userAgent(),
                session.ipAddress()
            ))
            .toList();
    }

    @Override
    @Transactional
    public SessionRevocationResult revokeSession(String sessionId) {
        AuthenticatedUser authenticatedUser = requiredAuthenticatedUser();
        UUID parsedSessionId = parseSessionId(sessionId);
        boolean revoked = sessionPersistencePort.revokeSession(authenticatedUser.userId(), parsedSessionId, clockPort.now());
        if (!revoked) {
            throw new IdentityException("identity.session_not_found", "Session was not found.", IdentityErrorType.NOT_FOUND);
        }
        return new SessionRevocationResult(parsedSessionId.equals(authenticatedUser.sessionId()));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        AuthenticatedUser authenticatedUser = requiredAuthenticatedUser();
        CredentialRules.validatePassword(command.newPassword());

        StoredUser user = userPersistencePort.findById(authenticatedUser.userId())
            .orElseThrow(() -> new IdentityException("identity.user_not_found", "User account was not found.", IdentityErrorType.UNAUTHORIZED));

        if (user.passwordHash() == null || !passwordHasher.matches(command.currentPassword(), user.passwordHash())) {
            throw new IdentityException("identity.password_mismatch", "Current password is incorrect.", IdentityErrorType.BAD_REQUEST);
        }

        Instant now = clockPort.now();
        userPersistencePort.updatePassword(user.id(), passwordHasher.hash(command.newPassword()));
        passwordResetTokenPersistencePort.invalidateOutstandingTokens(user.id(), now);
        sessionPersistencePort.revokeAllOtherSessions(user.id(), authenticatedUser.sessionId(), now);
    }

    @Override
    @Transactional
    public void requestPasswordReset(RequestPasswordResetCommand command) {
        String email = CredentialRules.normalizeEmail(command.email());
        Optional<StoredUser> user = userPersistencePort.findByEmail(email)
            .filter(candidate -> !candidate.deleted() && candidate.passwordHash() != null);
        if (user.isEmpty()) {
            return;
        }

        Instant now = clockPort.now();
        var issuedSecret = PasswordResetSecret.issue();
        StoredUser storedUser = user.orElseThrow();
        passwordResetTokenPersistencePort.invalidateOutstandingTokens(storedUser.id(), now);
        passwordResetTokenPersistencePort.create(new NewPasswordResetTokenRecord(
            UUID.randomUUID(),
            storedUser.id(),
            issuedSecret.tokenHash(),
            now,
            now.plus(identitySettings.passwordResetTtl())
        ));
        resetNotificationPort.sendPasswordReset(new PasswordResetNotification(
            storedUser.id(),
            storedUser.email(),
            issuedSecret.rawToken(),
            command.baseUrl(),
            now.plus(identitySettings.passwordResetTtl())
        ));
    }

    @Override
    @Transactional
    public void resetPassword(ConsumePasswordResetCommand command) {
        CredentialRules.validatePassword(command.newPassword());
        Instant now = clockPort.now();
        StoredPasswordResetToken token = passwordResetTokenPersistencePort.findUsableByTokenHash(
            PasswordResetSecret.hash(requiredTrimmed(command.token(), "token", "Password reset token is required.")),
            now
        ).orElseThrow(this::invalidResetToken);

        if (!passwordResetTokenPersistencePort.markUsed(token.id(), now)) {
            throw invalidResetToken();
        }
        userPersistencePort.updatePassword(token.userId(), passwordHasher.hash(command.newPassword()));
        passwordResetTokenPersistencePort.invalidateOutstandingTokens(token.userId(), now);
        sessionPersistencePort.revokeAllSessions(token.userId(), now);
    }

    @Override
    @Transactional
    public void deleteAccount(DeleteAccountCommand command) {
        AuthenticatedUser authenticatedUser = requiredAuthenticatedUser();
        StoredUser user = userPersistencePort.findById(authenticatedUser.userId())
            .orElseThrow(() -> new IdentityException("identity.user_not_found", "User account was not found.", IdentityErrorType.UNAUTHORIZED));

        if (user.passwordHash() == null || !passwordHasher.matches(command.currentPassword(), user.passwordHash())) {
            throw new IdentityException("identity.password_mismatch", "Current password is incorrect.", IdentityErrorType.BAD_REQUEST);
        }

        Instant now = clockPort.now();
        TombstoneIdentity tombstoneIdentity = TombstoneIdentity.forUser(user.id());
        sessionPersistencePort.revokeAllSessions(user.id(), now);
        passwordResetTokenPersistencePort.invalidateOutstandingTokens(user.id(), now);
        userPersistencePort.tombstone(new TombstoneUserRecord(
            user.id(),
            tombstoneIdentity.email(),
            tombstoneIdentity.username(),
            tombstoneIdentity.displayName(),
            now
        ));
        accountDeletionImpactPort.handleAccountDeleted(new AccountDeletionImpact(user.id(), now));
    }

    @Override
    @Transactional
    public Optional<AuthenticatedSession> authenticateSession(String sessionId, ClientContext clientContext) {
        UUID parsedSessionId;
        try {
            parsedSessionId = UUID.fromString(sessionId);
        }
        catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        Instant now = clockPort.now();
        Optional<StoredSession> session = sessionPersistencePort.findActiveById(parsedSessionId, now);
        if (session.isEmpty()) {
            return Optional.empty();
        }

        StoredUser user = userPersistencePort.findById(session.orElseThrow().userId())
            .filter(candidate -> !candidate.deleted() && candidate.passwordHash() != null)
            .orElse(null);
        if (user == null) {
            return Optional.empty();
        }

        ClientContext resolvedContext = defaultContext(clientContext);
        sessionPersistencePort.touch(
            parsedSessionId,
            now,
            truncate(resolvedContext.userAgent(), MAX_USER_AGENT_LENGTH),
            truncate(resolvedContext.ipAddress(), MAX_IP_LENGTH)
        );

        return Optional.of(new AuthenticatedSession(user.id(), parsedSessionId, user.username(), user.displayName(), session.orElseThrow().expiresAt()));
    }

    private AuthenticatedUser requiredAuthenticatedUser() {
        return authenticatedUserPort.currentUser()
            .orElseThrow(() -> new IdentityException(
                "identity.unauthenticated",
                "Authentication is required for this operation.",
                IdentityErrorType.UNAUTHORIZED
            ));
    }

    private IdentityException invalidCredentials() {
        return new IdentityException(
            "identity.invalid_credentials",
            "Email or password is incorrect.",
            IdentityErrorType.UNAUTHORIZED
        );
    }

    private IdentityException invalidResetToken() {
        return new IdentityException(
            "identity.reset_token_invalid",
            "Password reset token is invalid or expired.",
            IdentityErrorType.BAD_REQUEST
        );
    }

    private static IdentityException duplicateRegistrationConflict(DuplicateUserIdentityException exception) {
        return switch (exception.conflictTarget()) {
            case EMAIL -> new IdentityException("identity.email_taken", "Email is already registered.", IdentityErrorType.CONFLICT);
            case USERNAME -> new IdentityException("identity.username_taken", "Username is already registered.", IdentityErrorType.CONFLICT);
            case UNKNOWN -> new IdentityException(
                "identity.registration_conflict",
                "Registration could not be completed because the identity is already in use.",
                IdentityErrorType.CONFLICT
            );
        };
    }

    private static UUID parseSessionId(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        }
        catch (IllegalArgumentException exception) {
            throw new IdentityException("identity.invalid_session_id", "Session id must be a UUID.", IdentityErrorType.BAD_REQUEST);
        }
    }

    private static String requiredTrimmed(String value, String field, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IdentityException("identity.missing_" + field, message, IdentityErrorType.BAD_REQUEST);
        }
        return trimmed;
    }

    private static ClientContext defaultContext(ClientContext clientContext) {
        return clientContext == null ? new ClientContext("", "") : clientContext;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
