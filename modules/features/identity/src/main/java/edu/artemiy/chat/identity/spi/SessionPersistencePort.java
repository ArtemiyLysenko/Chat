package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionPersistencePort {

    StoredSession create(NewSessionRecord newSession);

    Optional<StoredSession> findActiveById(UUID sessionId, Instant now);

    List<StoredSession> findActiveByUserId(UUID userId, Instant now);

    void touch(UUID sessionId, Instant lastSeenAt, String userAgent, String ipAddress);

    boolean revokeSession(UUID userId, UUID sessionId, Instant revokedAt);

    List<UUID> revokeAllSessions(UUID userId, Instant revokedAt);

    List<UUID> revokeAllOtherSessions(UUID userId, UUID currentSessionId, Instant revokedAt);
}
