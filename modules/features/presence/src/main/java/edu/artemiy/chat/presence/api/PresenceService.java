package edu.artemiy.chat.presence.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PresenceService {

    PresenceState registerTabConnection(UUID userId, UUID sessionId, String tabKey);

    PresenceState recordTabActivity(UUID userId, UUID sessionId, String tabKey, Instant lastActivityAt);

    PresenceState closeTab(UUID userId, UUID sessionId, String tabKey);

    PresenceState derivePresence(UUID userId);

    Map<UUID, PresenceState> derivePresence(Collection<UUID> userIds);
}
