package edu.artemiy.chat.presence.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PresencePersistencePort {

    void registerTab(NewSessionTabRecord record);

    void recordActivity(SessionTabActivityRecord record);

    void closeTab(UUID sessionId, String tabKey, Instant closedAt);

    List<StoredSessionTab> listOpenTabsByUserIds(Collection<UUID> userIds, Instant now);
}
