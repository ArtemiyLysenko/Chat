package edu.artemiy.chat.app.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.identity.api.AuthenticatedSession;
import edu.artemiy.chat.presence.api.PresenceService;
import edu.artemiy.chat.presence.api.PresenceState;

@Component
class ChatWebSocketPresenceCoordinator {

    static final String TAB_KEY_ATTRIBUTE = ChatWebSocketPresenceCoordinator.class.getName() + ".tabKey";
    private static final String TAB_CLOSED_ATTRIBUTE = ChatWebSocketPresenceCoordinator.class.getName() + ".tabClosed";
    private static final int SESSION_REVOKED_CLOSE_CODE = 4401;
    // Keep the app-layer timeout aligned with the 45-second connected-tab rule plus a small guard band.
    private static final Duration PING_EXPIRY_DELAY = Duration.ofSeconds(46);

    private final ClockPort clockPort;
    private final PresenceService presenceService;
    private final ChatWebSocketEventRelay eventRelay;
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "chat-ws-presence-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ScheduledFuture<?>> timeoutTasksBySocketId = new ConcurrentHashMap<>();
    private final Map<UUID, PresenceState> lastPresenceByUserId = new ConcurrentHashMap<>();

    ChatWebSocketPresenceCoordinator(
        ClockPort clockPort,
        PresenceService presenceService,
        ChatWebSocketEventRelay eventRelay
    ) {
        this.clockPort = clockPort;
        this.presenceService = presenceService;
        this.eventRelay = eventRelay;
    }

    void handleTabActivity(WebSocketSession session, AuthenticatedSession authenticatedSession, String tabKey, Instant lastActivityAt) {
        String storedTabKey = storedTabKey(session);
        String normalizedTabKey = bindTabKey(session, tabKey);
        PresenceState previousState = currentPresence(authenticatedSession.userId());
        PresenceState currentState = storedTabKey == null
            ? presenceService.registerTabConnection(authenticatedSession.userId(), authenticatedSession.sessionId(), normalizedTabKey)
            : presenceService.recordTabActivity(authenticatedSession.userId(), authenticatedSession.sessionId(), normalizedTabKey, lastActivityAt);
        session.getAttributes().put(TAB_CLOSED_ATTRIBUTE, Boolean.FALSE);
        updatePresence(authenticatedSession.userId(), previousState, currentState);
        schedulePingExpiryCheck(session.getId(), authenticatedSession.userId());
    }

    void handleTabClosed(WebSocketSession session, AuthenticatedSession authenticatedSession, String tabKey) {
        closeTrackedTab(session, authenticatedSession, bindTabKey(session, tabKey), true);
    }

    void handleConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (!shouldCloseTab(status)) {
            return;
        }
        AuthenticatedSession authenticatedSession = authenticatedSession(session);
        String tabKey = storedTabKey(session);
        if (authenticatedSession == null || tabKey == null) {
            cancelPingExpiryCheck(session.getId());
            return;
        }
        closeTrackedTab(session, authenticatedSession, tabKey, false);
    }

    @PreDestroy
    void shutdown() {
        timeoutExecutor.shutdownNow();
    }

    private void closeTrackedTab(
        WebSocketSession session,
        AuthenticatedSession authenticatedSession,
        String tabKey,
        boolean markClosed
    ) {
        cancelPingExpiryCheck(session.getId());
        if (Boolean.TRUE.equals(session.getAttributes().get(TAB_CLOSED_ATTRIBUTE))) {
            return;
        }
        PresenceState previousState = currentPresence(authenticatedSession.userId());
        PresenceState currentState = presenceService.closeTab(authenticatedSession.userId(), authenticatedSession.sessionId(), tabKey);
        if (markClosed) {
            session.getAttributes().put(TAB_CLOSED_ATTRIBUTE, Boolean.TRUE);
        }
        updatePresence(authenticatedSession.userId(), previousState, currentState);
    }

    private void schedulePingExpiryCheck(String socketId, UUID userId) {
        cancelPingExpiryCheck(socketId);
        timeoutTasksBySocketId.put(socketId, timeoutExecutor.schedule(() -> {
            timeoutTasksBySocketId.remove(socketId);
            refreshPresence(userId);
        }, PING_EXPIRY_DELAY.toMillis(), TimeUnit.MILLISECONDS));
    }

    private void cancelPingExpiryCheck(String socketId) {
        ScheduledFuture<?> existingTask = timeoutTasksBySocketId.remove(socketId);
        if (existingTask != null) {
            existingTask.cancel(false);
        }
    }

    private void refreshPresence(UUID userId) {
        PresenceState currentState = presenceService.derivePresence(userId);
        PresenceState previousState = lastPresenceByUserId.put(userId, currentState);
        if (previousState != null && previousState != currentState) {
            eventRelay.relayPresenceUpdated(userId, currentState, clockPort.now());
        }
    }

    private PresenceState currentPresence(UUID userId) {
        return lastPresenceByUserId.computeIfAbsent(userId, presenceService::derivePresence);
    }

    private void updatePresence(UUID userId, PresenceState previousState, PresenceState currentState) {
        lastPresenceByUserId.put(userId, currentState);
        if (previousState != currentState) {
            eventRelay.relayPresenceUpdated(userId, currentState, clockPort.now());
        }
    }

    private static AuthenticatedSession authenticatedSession(WebSocketSession session) {
        return (AuthenticatedSession) session.getAttributes()
            .get(ChatWebSocketHandshakeInterceptor.AUTHENTICATED_SESSION_ATTRIBUTE);
    }

    private static boolean shouldCloseTab(CloseStatus status) {
        return status.getCode() == CloseStatus.NORMAL.getCode()
            || status.getCode() == CloseStatus.GOING_AWAY.getCode()
            || status.getCode() == SESSION_REVOKED_CLOSE_CODE;
    }

    private static String bindTabKey(WebSocketSession session, String tabKey) {
        if (tabKey == null || tabKey.isBlank()) {
            throw new IllegalArgumentException("Tab key is required.");
        }
        String normalizedTabKey = tabKey.trim();
        String storedTabKey = storedTabKey(session);
        if (storedTabKey != null && !storedTabKey.equals(normalizedTabKey)) {
            throw new IllegalArgumentException("WebSocket tab key cannot change after the first activity frame.");
        }
        session.getAttributes().put(TAB_KEY_ATTRIBUTE, normalizedTabKey);
        return normalizedTabKey;
    }

    private static String storedTabKey(WebSocketSession session) {
        Object storedValue = session.getAttributes().get(TAB_KEY_ATTRIBUTE);
        return storedValue instanceof String value ? value : null;
    }
}
