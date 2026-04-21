package edu.artemiy.chat.app.websocket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import edu.artemiy.chat.identity.api.AuthenticatedSession;

@Component
class ChatWebSocketConnectionRegistry {

    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    private final Map<String, ConnectedSession> sessionsBySocketId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> socketIdsByUserId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> socketIdsByAuthSessionId = new ConcurrentHashMap<>();

    void register(WebSocketSession session, AuthenticatedSession authenticatedSession) {
        ConnectedSession connectedSession = new ConnectedSession(
            authenticatedSession.userId(),
            authenticatedSession.sessionId(),
            new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT_BYTES)
        );
        sessionsBySocketId.put(session.getId(), connectedSession);
        socketIdsByUserId.computeIfAbsent(authenticatedSession.userId(), ignored -> ConcurrentHashMap.newKeySet()).add(session.getId());
        socketIdsByAuthSessionId.computeIfAbsent(authenticatedSession.sessionId(), ignored -> ConcurrentHashMap.newKeySet()).add(session.getId());
    }

    void unregister(String socketId) {
        ConnectedSession connectedSession = sessionsBySocketId.remove(socketId);
        if (connectedSession == null) {
            return;
        }
        removeFromIndex(socketIdsByUserId, connectedSession.userId(), socketId);
        removeFromIndex(socketIdsByAuthSessionId, connectedSession.authSessionId(), socketId);
    }

    List<WebSocketSession> userConnections(Collection<UUID> userIds) {
        List<WebSocketSession> sessions = new ArrayList<>();
        for (UUID userId : userIds) {
            sessions.addAll(indexedSessions(socketIdsByUserId.getOrDefault(userId, Set.of())));
        }
        return sessions;
    }

    List<WebSocketSession> authSessionConnections(UUID authSessionId) {
        return indexedSessions(socketIdsByAuthSessionId.getOrDefault(authSessionId, Set.of()));
    }

    private List<WebSocketSession> indexedSessions(Set<String> socketIds) {
        return socketIds.stream()
            .map(sessionsBySocketId::get)
            .filter(connectedSession -> connectedSession != null)
            .map(ConnectedSession::webSocketSession)
            .toList();
    }

    private static void removeFromIndex(Map<UUID, Set<String>> index, UUID key, String socketId) {
        index.computeIfPresent(key, (ignored, socketIds) -> {
            socketIds.remove(socketId);
            return socketIds.isEmpty() ? null : socketIds;
        });
    }

    private record ConnectedSession(UUID userId, UUID authSessionId, WebSocketSession webSocketSession) {
    }
}
