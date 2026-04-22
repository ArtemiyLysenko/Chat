package edu.artemiy.chat.app.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import edu.artemiy.chat.identity.api.SessionRevokedEvent;
import edu.artemiy.chat.messaging.api.ChatAudienceQuery;
import edu.artemiy.chat.messaging.api.ChatMessageEvent;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatUnreadCount;
import edu.artemiy.chat.messaging.api.MessageEventType;
import edu.artemiy.chat.messaging.api.MessagingException;
import edu.artemiy.chat.messaging.api.MessagingService;
import edu.artemiy.chat.messaging.api.UnreadMarkerUpdatedEvent;
import edu.artemiy.chat.presence.api.PresenceAudienceQuery;
import edu.artemiy.chat.presence.api.PresenceState;
import tools.jackson.databind.ObjectMapper;

@Component
class ChatWebSocketEventRelay {

    private static final CloseStatus SESSION_REVOKED = new CloseStatus(4401, "Session revoked.");

    private final ChatWebSocketConnectionRegistry connectionRegistry;
    private final ChatAudienceQuery chatAudienceQuery;
    private final MessagingService messagingService;
    private final PresenceAudienceQuery presenceAudienceQuery;
    private final ObjectMapper objectMapper;

    ChatWebSocketEventRelay(
        ChatWebSocketConnectionRegistry connectionRegistry,
        ChatAudienceQuery chatAudienceQuery,
        MessagingService messagingService,
        PresenceAudienceQuery presenceAudienceQuery,
        ObjectMapper objectMapper
    ) {
        this.connectionRegistry = connectionRegistry;
        this.chatAudienceQuery = chatAudienceQuery;
        this.messagingService = messagingService;
        this.presenceAudienceQuery = presenceAudienceQuery;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void relayMessageEvent(ChatMessageEvent event) {
        ChatWebSocketEventEnvelope envelope = new ChatWebSocketEventEnvelope(
            UUID.randomUUID(),
            event.type().eventType(),
            event.occurredAt(),
            event.message().chat(),
            event.message()
        );
        sendToUsers(chatAudienceQuery.listAudienceUserIds(event.message().chat()), envelope);

        if (event.type() == MessageEventType.CREATED) {
            chatAudienceQuery.listAudienceUserIds(event.message().chat()).stream()
                .filter(userId -> !userId.equals(event.actorUserId()))
                .forEach(userId -> relayUnreadUpdate(userId, event.message().chat(), null, event.occurredAt()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void relayUnreadMarkerUpdatedEvent(UnreadMarkerUpdatedEvent event) {
        relayUnreadUpdate(event.userId(), event.chat(), event.lastReadMessageId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void relaySessionRevokedEvent(SessionRevokedEvent event) {
        for (UUID sessionId : event.sessionIds()) {
            ChatWebSocketEventEnvelope envelope = new ChatWebSocketEventEnvelope(
                UUID.randomUUID(),
                "session.revoked",
                event.occurredAt(),
                null,
                new SessionRevokedPayload(sessionId)
            );
            sendAndClose(connectionRegistry.authSessionConnections(sessionId), envelope, SESSION_REVOKED);
        }
    }

    void relayPresenceUpdated(UUID userId, PresenceState presenceState, Instant occurredAt) {
        sendToUsers(
            presenceAudienceQuery.listAudienceUserIds(userId),
            new ChatWebSocketEventEnvelope(
                UUID.randomUUID(),
                "presence.updated",
                occurredAt,
                null,
                new PresenceUpdatedPayload(userId, presenceState)
            )
        );
    }

    private void relayUnreadUpdate(UUID userId, ChatTargetRef chat, UUID lastReadMessageId, Instant occurredAt) {
        Integer unreadCount = unreadCount(userId, chat);
        if (unreadCount == null) {
            return;
        }
        sendToUsers(List.of(userId), new ChatWebSocketEventEnvelope(
            UUID.randomUUID(),
            "unread.updated",
            occurredAt,
            chat,
            new UnreadUpdatedPayload(unreadCount, lastReadMessageId, occurredAt)
        ));
    }

    private Integer unreadCount(UUID userId, ChatTargetRef chat) {
        try {
            return messagingService.listUnreadCounts(userId, List.of(chat)).stream()
                .findFirst()
                .map(ChatUnreadCount::unreadCount)
                .orElse(0);
        }
        catch (MessagingException exception) {
            return null;
        }
    }

    private void sendToUsers(Collection<UUID> userIds, ChatWebSocketEventEnvelope envelope) {
        List<WebSocketSession> sessions = connectionRegistry.userConnections(userIds);
        TextMessage textMessage = serialize(envelope);
        for (WebSocketSession session : sessions) {
            send(session, textMessage);
        }
    }

    private void sendAndClose(List<WebSocketSession> sessions, ChatWebSocketEventEnvelope envelope, CloseStatus closeStatus) {
        TextMessage textMessage = serialize(envelope);
        for (WebSocketSession session : sessions) {
            try {
                send(session, textMessage);
            }
            finally {
                close(session, closeStatus);
            }
        }
    }

    private TextMessage serialize(ChatWebSocketEventEnvelope envelope) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(envelope));
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to serialize WebSocket event " + envelope.type(), exception);
        }
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(message);
        }
        catch (IOException exception) {
            close(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        }
        catch (IOException ignored) {
        }
        finally {
            connectionRegistry.unregister(session.getId());
        }
    }

    private record UnreadUpdatedPayload(int unreadCount, UUID lastReadMessageId, Instant updatedAt) {
    }

    private record SessionRevokedPayload(UUID sessionId) {
    }

    private record PresenceUpdatedPayload(UUID userId, PresenceState presence) {
    }
}
