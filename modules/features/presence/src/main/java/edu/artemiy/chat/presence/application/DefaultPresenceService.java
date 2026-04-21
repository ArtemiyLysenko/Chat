package edu.artemiy.chat.presence.application;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.presence.api.PresenceService;
import edu.artemiy.chat.presence.api.PresenceState;
import edu.artemiy.chat.presence.domain.PresenceDerivationRules;
import edu.artemiy.chat.presence.spi.NewSessionTabRecord;
import edu.artemiy.chat.presence.spi.PresencePersistencePort;
import edu.artemiy.chat.presence.spi.SessionTabActivityRecord;
import edu.artemiy.chat.presence.spi.StoredSessionTab;

@Service
public class DefaultPresenceService implements PresenceService {

    private static final int MAX_TAB_KEY_LENGTH = 128;

    private final ClockPort clockPort;
    private final PresencePersistencePort presencePersistencePort;

    public DefaultPresenceService(ClockPort clockPort, PresencePersistencePort presencePersistencePort) {
        this.clockPort = clockPort;
        this.presencePersistencePort = presencePersistencePort;
    }

    @Override
    @Transactional
    public PresenceState registerTabConnection(UUID userId, UUID sessionId, String tabKey) {
        Instant now = clockPort.now();
        presencePersistencePort.registerTab(new NewSessionTabRecord(
            sessionId,
            normalizeTabKey(tabKey),
            now,
            now,
            now
        ));
        return derivePresence(userId);
    }

    @Override
    @Transactional
    public PresenceState recordTabActivity(UUID userId, UUID sessionId, String tabKey, Instant lastActivityAt) {
        Instant now = clockPort.now();
        presencePersistencePort.recordActivity(new SessionTabActivityRecord(
            sessionId,
            normalizeTabKey(tabKey),
            normalizeActivityTime(lastActivityAt, now),
            now
        ));
        return derivePresence(userId);
    }

    @Override
    @Transactional
    public PresenceState closeTab(UUID userId, UUID sessionId, String tabKey) {
        presencePersistencePort.closeTab(sessionId, normalizeTabKey(tabKey), clockPort.now());
        return derivePresence(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PresenceState derivePresence(UUID userId) {
        return derivePresence(List.of(userId)).getOrDefault(userId, PresenceState.OFFLINE);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PresenceState> derivePresence(Collection<UUID> userIds) {
        List<UUID> normalizedUserIds = userIds.stream()
            .distinct()
            .toList();
        if (normalizedUserIds.isEmpty()) {
            return Map.of();
        }

        Instant now = clockPort.now();
        Map<UUID, List<StoredSessionTab>> tabsByUserId = presencePersistencePort.listOpenTabsByUserIds(normalizedUserIds, now).stream()
            .collect(Collectors.groupingBy(StoredSessionTab::userId));

        Map<UUID, PresenceState> presenceByUserId = new LinkedHashMap<>();
        for (UUID userId : normalizedUserIds) {
            presenceByUserId.put(userId, PresenceDerivationRules.derive(now, tabsByUserId.getOrDefault(userId, List.of())));
        }
        return presenceByUserId;
    }

    private static Instant normalizeActivityTime(Instant lastActivityAt, Instant now) {
        if (lastActivityAt == null || lastActivityAt.isAfter(now)) {
            return now;
        }
        return lastActivityAt;
    }

    private static String normalizeTabKey(String tabKey) {
        if (tabKey == null) {
            throw new IllegalArgumentException("Tab key is required.");
        }
        String normalized = tabKey.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Tab key is required.");
        }
        if (normalized.length() > MAX_TAB_KEY_LENGTH) {
            throw new IllegalArgumentException("Tab key must be 128 characters or fewer.");
        }
        return normalized;
    }
}
