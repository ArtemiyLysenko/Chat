package edu.artemiy.chat.adapters.persistence.jpa.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.identity.spi.NewSessionRecord;
import edu.artemiy.chat.identity.spi.SessionPersistencePort;
import edu.artemiy.chat.identity.spi.StoredSession;

@Component
class JpaSessionPersistenceAdapter implements SessionPersistencePort {

    private final UserSessionJpaRepository userSessionJpaRepository;

    JpaSessionPersistenceAdapter(UserSessionJpaRepository userSessionJpaRepository) {
        this.userSessionJpaRepository = userSessionJpaRepository;
    }

    @Override
    public StoredSession create(NewSessionRecord newSession) {
        UserSessionEntity saved = userSessionJpaRepository.save(new UserSessionEntity(
            newSession.id(),
            newSession.userId(),
            newSession.createdAt(),
            newSession.lastSeenAt(),
            newSession.expiresAt(),
            null,
            newSession.userAgent(),
            newSession.ipAddress()
        ));
        return toStoredSession(saved);
    }

    @Override
    public Optional<StoredSession> findActiveById(UUID sessionId, Instant now) {
        return userSessionJpaRepository.findActiveById(sessionId, now).map(JpaSessionPersistenceAdapter::toStoredSession);
    }

    @Override
    public List<StoredSession> findActiveByUserId(UUID userId, Instant now) {
        return userSessionJpaRepository.findActiveByUserId(userId, now).stream()
            .map(JpaSessionPersistenceAdapter::toStoredSession)
            .toList();
    }

    @Override
    @Transactional
    public void touch(UUID sessionId, Instant lastSeenAt, String userAgent, String ipAddress) {
        userSessionJpaRepository.touch(sessionId, lastSeenAt, userAgent, ipAddress);
    }

    @Override
    @Transactional
    public boolean revokeSession(UUID userId, UUID sessionId, Instant revokedAt) {
        return userSessionJpaRepository.revokeSession(userId, sessionId, revokedAt) > 0;
    }

    @Override
    @Transactional
    public void revokeAllSessions(UUID userId, Instant revokedAt) {
        userSessionJpaRepository.revokeAllSessions(userId, revokedAt);
    }

    @Override
    @Transactional
    public void revokeAllOtherSessions(UUID userId, UUID currentSessionId, Instant revokedAt) {
        userSessionJpaRepository.revokeAllOtherSessions(userId, currentSessionId, revokedAt);
    }

    private static StoredSession toStoredSession(UserSessionEntity entity) {
        return new StoredSession(
            entity.getId(),
            entity.getUserId(),
            entity.getCreatedAt(),
            entity.getLastSeenAt(),
            entity.getExpiresAt(),
            entity.getRevokedAt(),
            entity.getUserAgent(),
            entity.getIpAddress()
        );
    }
}
