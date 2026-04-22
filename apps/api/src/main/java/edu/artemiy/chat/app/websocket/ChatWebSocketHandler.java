package edu.artemiy.chat.app.websocket;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import edu.artemiy.chat.identity.api.AuthenticatedSession;
import tools.jackson.databind.ObjectMapper;

@Component
class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final CloseStatus INVALID_MESSAGE = new CloseStatus(1007, "Invalid WebSocket message.");

    private final ChatWebSocketConnectionRegistry connectionRegistry;
    private final ChatWebSocketPresenceCoordinator presenceCoordinator;
    private final ObjectMapper objectMapper;

    ChatWebSocketHandler(
        ChatWebSocketConnectionRegistry connectionRegistry,
        ChatWebSocketPresenceCoordinator presenceCoordinator,
        ObjectMapper objectMapper
    ) {
        this.connectionRegistry = connectionRegistry;
        this.presenceCoordinator = presenceCoordinator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AuthenticatedSession authenticatedSession = (AuthenticatedSession) session.getAttributes()
            .get(ChatWebSocketHandshakeInterceptor.AUTHENTICATED_SESSION_ATTRIBUTE);
        if (authenticatedSession == null) {
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        connectionRegistry.register(session, authenticatedSession);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientControlMessage controlMessage = parseControlMessage(session, message);
        if (controlMessage == null) {
            return;
        }

        try {
            switch (controlMessage.type()) {
                case "subscription.resume" -> {
                    return;
                }
                case "tab.activity" -> presenceCoordinator.handleTabActivity(
                    session,
                    authenticatedSession(session),
                    controlMessage.tabKey(),
                    controlMessage.lastActivityAt()
                );
                case "tab.closed" -> presenceCoordinator.handleTabClosed(
                    session,
                    authenticatedSession(session),
                    controlMessage.tabKey()
                );
                default -> closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            }
        } catch (IllegalArgumentException exception) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connectionRegistry.unregister(session.getId());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        presenceCoordinator.handleConnectionClosed(session, status);
        connectionRegistry.unregister(session.getId());
    }

    private ClientControlMessage parseControlMessage(WebSocketSession session, TextMessage message) {
        try {
            return objectMapper.readValue(message.getPayload(), ClientControlMessage.class);
        }
        catch (RuntimeException exception) {
            closeQuietly(session, INVALID_MESSAGE);
            return null;
        }
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        }
        catch (IOException ignored) {
        }
    }

    private static AuthenticatedSession authenticatedSession(WebSocketSession session) {
        AuthenticatedSession authenticatedSession = (AuthenticatedSession) session.getAttributes()
            .get(ChatWebSocketHandshakeInterceptor.AUTHENTICATED_SESSION_ATTRIBUTE);
        if (authenticatedSession == null) {
            throw new IllegalArgumentException("Authenticated session is required.");
        }
        return authenticatedSession;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClientControlMessage(String type, String tabKey, Instant lastActivityAt, String lastEventId) {
    }
}
