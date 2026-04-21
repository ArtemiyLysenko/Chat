package edu.artemiy.chat.messaging.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import edu.artemiy.chat.messaging.api.ChatTargetRef;

public interface MessagingPersistencePort {

    StoredMessage createMessage(NewMessageRecord message);

    Optional<StoredMessage> findMessage(UUID messageId);

    List<StoredMessage> listLatestMessages(ChatTargetRef chat, int limit);

    List<StoredMessage> listMessagesBefore(ChatTargetRef chat, Instant beforeCreatedAt, UUID beforeMessageId, int limit);

    Optional<StoredUnreadMarker> findUnreadMarker(UUID userId, ChatTargetRef chat);

    StoredUnreadMarker saveUnreadMarker(UUID userId, ChatTargetRef chat, UUID lastReadMessageId, Instant updatedAt);
}
