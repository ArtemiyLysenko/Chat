package edu.artemiy.chat.adapters.persistence.jpa.presence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.presence.spi.NewSessionTabRecord;
import edu.artemiy.chat.presence.spi.PresencePersistencePort;
import edu.artemiy.chat.presence.spi.SessionTabActivityRecord;
import edu.artemiy.chat.presence.spi.StoredSessionTab;

@Component
class JpaPresencePersistenceAdapter implements PresencePersistencePort {

    private final SessionTabJpaRepository sessionTabJpaRepository;

    JpaPresencePersistenceAdapter(SessionTabJpaRepository sessionTabJpaRepository) {
        this.sessionTabJpaRepository = sessionTabJpaRepository;
    }

    @Override
    @Transactional
    public void registerTab(NewSessionTabRecord record) {
        SessionTabEntity entity = sessionTabJpaRepository.findBySessionIdAndTabKey(record.sessionId(), record.tabKey())
            .orElseGet(() -> new SessionTabEntity(
                UUID.randomUUID(),
                record.sessionId(),
                record.tabKey(),
                record.connectedAt(),
                record.lastActivityAt(),
                record.lastPingAt(),
                null
            ));
        entity.setConnectedAt(record.connectedAt());
        entity.setLastActivityAt(latest(entity.getLastActivityAt(), record.lastActivityAt()));
        entity.setLastPingAt(latest(entity.getLastPingAt(), record.lastPingAt()));
        entity.setClosedAt(null);
        sessionTabJpaRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void recordActivity(SessionTabActivityRecord record) {
        SessionTabEntity entity = sessionTabJpaRepository.findBySessionIdAndTabKey(record.sessionId(), record.tabKey())
            .orElseGet(() -> new SessionTabEntity(
                UUID.randomUUID(),
                record.sessionId(),
                record.tabKey(),
                record.lastPingAt(),
                record.lastActivityAt(),
                record.lastPingAt(),
                null
            ));
        entity.setLastActivityAt(latest(entity.getLastActivityAt(), record.lastActivityAt()));
        entity.setLastPingAt(latest(entity.getLastPingAt(), record.lastPingAt()));
        entity.setClosedAt(null);
        sessionTabJpaRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void closeTab(UUID sessionId, String tabKey, Instant closedAt) {
        sessionTabJpaRepository.findBySessionIdAndTabKey(sessionId, tabKey).ifPresent(entity -> {
            entity.setClosedAt(closedAt);
            sessionTabJpaRepository.saveAndFlush(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredSessionTab> listOpenTabsByUserIds(Collection<UUID> userIds, Instant now) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return sessionTabJpaRepository.findOpenTabsByUserIds(userIds, now).stream()
            .map(JpaPresencePersistenceAdapter::toStoredSessionTab)
            .toList();
    }

    private static StoredSessionTab toStoredSessionTab(SessionTabJpaRepository.SessionTabPresenceProjection projection) {
        return new StoredSessionTab(
            projection.getId(),
            projection.getUserId(),
            projection.getSessionId(),
            projection.getTabKey(),
            projection.getConnectedAt(),
            projection.getLastActivityAt(),
            projection.getLastPingAt(),
            projection.getClosedAt()
        );
    }

    private static Instant latest(Instant currentValue, Instant candidate) {
        if (currentValue == null) {
            return candidate;
        }
        if (candidate == null || candidate.isBefore(currentValue)) {
            return currentValue;
        }
        return candidate;
    }
}
