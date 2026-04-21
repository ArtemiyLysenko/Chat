package edu.artemiy.chat.messaging.api;

import java.util.UUID;
import java.util.List;

public interface MessagingService {

    ChatMessage sendMessage(UUID actorUserId, SendMessageCommand command);

    ChatMessage editMessage(UUID actorUserId, EditMessageCommand command);

    ChatMessage deleteMessage(UUID actorUserId, UUID messageId);

    MessageHistoryPage readMessageHistory(UUID actorUserId, ReadMessageHistoryQuery query);

    UnreadMarker advanceReadMarker(UUID actorUserId, AdvanceReadMarkerCommand command);

    List<ChatUnreadCount> listUnreadCounts(UUID actorUserId, List<ChatTargetRef> chats);
}
