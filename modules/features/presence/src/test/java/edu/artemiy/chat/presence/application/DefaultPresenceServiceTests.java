package edu.artemiy.chat.presence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.artemiy.chat.presence.api.PresenceState;
import edu.artemiy.chat.presence.spi.NewSessionTabRecord;
import edu.artemiy.chat.presence.spi.PresencePersistencePort;
import edu.artemiy.chat.presence.spi.SessionTabActivityRecord;
import edu.artemiy.chat.presence.spi.StoredSessionTab;

class DefaultPresenceServiceTests {

    private static final Instant NOW = Instant.parse("2026-04-21T12:00:00Z");

    private final FakePresencePersistencePort presencePersistencePort = new FakePresencePersistencePort();
    private final DefaultPresenceService service = new DefaultPresenceService(() -> NOW, presencePersistencePort);

    @Test
    void registerTabConnectionMarksUserOnline() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        presencePersistencePort.linkSession(userId, sessionId);

        PresenceState presenceState = service.registerTabConnection(userId, sessionId, "tab-1");

        assertThat(presenceState).isEqualTo(PresenceState.ONLINE);
        assertThat(service.derivePresence(userId)).isEqualTo(PresenceState.ONLINE);
    }

    @Test
    void derivePresenceReturnsAfkWhenConnectedTabsAreInactive() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        presencePersistencePort.linkSession(userId, sessionId);
        presencePersistencePort.put(new StoredSessionTab(
            UUID.randomUUID(),
            userId,
            sessionId,
            "tab-1",
            NOW.minusSeconds(120),
            NOW.minusSeconds(61),
            NOW.minusSeconds(10),
            null
        ));

        assertThat(service.derivePresence(userId)).isEqualTo(PresenceState.AFK);
    }

    @Test
    void derivePresenceReturnsOfflineWhenOnlyTabsHaveTimedOut() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        presencePersistencePort.linkSession(userId, sessionId);
        presencePersistencePort.put(new StoredSessionTab(
            UUID.randomUUID(),
            userId,
            sessionId,
            "tab-1",
            NOW.minusSeconds(120),
            NOW.minusSeconds(20),
            NOW.minusSeconds(46),
            null
        ));

        assertThat(service.derivePresence(userId)).isEqualTo(PresenceState.OFFLINE);
    }

    @Test
    void derivePresenceUsesAnyActiveConnectedTabAcrossMultipleTabs() {
        UUID userId = UUID.randomUUID();
        UUID firstSessionId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();
        presencePersistencePort.linkSession(userId, firstSessionId);
        presencePersistencePort.linkSession(userId, secondSessionId);
        presencePersistencePort.put(new StoredSessionTab(
            UUID.randomUUID(),
            userId,
            firstSessionId,
            "tab-1",
            NOW.minusSeconds(300),
            NOW.minusSeconds(90),
            NOW.minusSeconds(15),
            null
        ));
        presencePersistencePort.put(new StoredSessionTab(
            UUID.randomUUID(),
            userId,
            secondSessionId,
            "tab-2",
            NOW.minusSeconds(180),
            NOW.minusSeconds(20),
            NOW.minusSeconds(15),
            null
        ));

        assertThat(service.derivePresence(userId)).isEqualTo(PresenceState.ONLINE);
    }

    @Test
    void derivePresenceTreatsBoundaryTimeoutsAsStillConnectedAndActive() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        presencePersistencePort.linkSession(userId, sessionId);
        presencePersistencePort.put(new StoredSessionTab(
            UUID.randomUUID(),
            userId,
            sessionId,
            "tab-1",
            NOW.minusSeconds(300),
            NOW.minusSeconds(60),
            NOW.minusSeconds(45),
            null
        ));

        assertThat(service.derivePresence(userId)).isEqualTo(PresenceState.ONLINE);
    }

    @Test
    void closeTabTransitionsUserOfflineWhenLastTabCloses() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        presencePersistencePort.linkSession(userId, sessionId);
        service.registerTabConnection(userId, sessionId, "tab-1");

        PresenceState closedState = service.closeTab(userId, sessionId, "tab-1");

        assertThat(closedState).isEqualTo(PresenceState.OFFLINE);
        assertThat(service.derivePresence(userId)).isEqualTo(PresenceState.OFFLINE);
    }

    @Test
    void derivePresenceForMultipleUsersDefaultsMissingUsersToOffline() {
        UUID onlineUserId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID offlineUserId = UUID.randomUUID();
        presencePersistencePort.linkSession(onlineUserId, sessionId);
        service.registerTabConnection(onlineUserId, sessionId, "tab-1");

        Map<UUID, PresenceState> presenceByUserId = service.derivePresence(List.of(onlineUserId, offlineUserId));

        assertThat(presenceByUserId).containsEntry(onlineUserId, PresenceState.ONLINE);
        assertThat(presenceByUserId).containsEntry(offlineUserId, PresenceState.OFFLINE);
    }

    @Test
    void recordTabActivityClampsFutureActivityToServerTime() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        presencePersistencePort.linkSession(userId, sessionId);

        service.recordTabActivity(userId, sessionId, "tab-1", NOW.plusSeconds(120));

        StoredSessionTab storedTab = presencePersistencePort.listOpenTabsByUserIds(List.of(userId), NOW).getFirst();
        assertThat(storedTab.lastActivityAt()).isEqualTo(NOW);
        assertThat(storedTab.lastPingAt()).isEqualTo(NOW);
    }

    private static final class FakePresencePersistencePort implements PresencePersistencePort {

        private final Map<String, StoredSessionTab> tabsByKey = new LinkedHashMap<>();
        private final Map<UUID, UUID> userIdBySessionId = new LinkedHashMap<>();

        void linkSession(UUID userId, UUID sessionId) {
            userIdBySessionId.put(sessionId, userId);
        }

        void put(StoredSessionTab tab) {
            tabsByKey.put(key(tab.sessionId(), tab.tabKey()), tab);
        }

        @Override
        public void registerTab(NewSessionTabRecord record) {
            StoredSessionTab existing = tabsByKey.get(key(record.sessionId(), record.tabKey()));
            UUID userId = userIdBySessionId.get(record.sessionId());
            StoredSessionTab updated = new StoredSessionTab(
                existing == null ? UUID.randomUUID() : existing.id(),
                userId,
                record.sessionId(),
                record.tabKey(),
                record.connectedAt(),
                latest(existing == null ? null : existing.lastActivityAt(), record.lastActivityAt()),
                latest(existing == null ? null : existing.lastPingAt(), record.lastPingAt()),
                null
            );
            put(updated);
        }

        @Override
        public void recordActivity(SessionTabActivityRecord record) {
            StoredSessionTab existing = tabsByKey.get(key(record.sessionId(), record.tabKey()));
            UUID userId = userIdBySessionId.get(record.sessionId());
            StoredSessionTab updated = new StoredSessionTab(
                existing == null ? UUID.randomUUID() : existing.id(),
                userId,
                record.sessionId(),
                record.tabKey(),
                existing == null ? record.lastPingAt() : existing.connectedAt(),
                latest(existing == null ? null : existing.lastActivityAt(), record.lastActivityAt()),
                latest(existing == null ? null : existing.lastPingAt(), record.lastPingAt()),
                null
            );
            put(updated);
        }

        @Override
        public void closeTab(UUID sessionId, String tabKey, Instant closedAt) {
            StoredSessionTab existing = tabsByKey.get(key(sessionId, tabKey));
            if (existing == null) {
                return;
            }
            put(new StoredSessionTab(
                existing.id(),
                existing.userId(),
                existing.sessionId(),
                existing.tabKey(),
                existing.connectedAt(),
                existing.lastActivityAt(),
                existing.lastPingAt(),
                closedAt
            ));
        }

        @Override
        public List<StoredSessionTab> listOpenTabsByUserIds(Collection<UUID> userIds, Instant now) {
            return tabsByKey.values().stream()
                .filter(tab -> userIds.contains(tab.userId()))
                .filter(tab -> tab.closedAt() == null)
                .toList();
        }

        private static String key(UUID sessionId, String tabKey) {
            return sessionId + "::" + tabKey;
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
}
