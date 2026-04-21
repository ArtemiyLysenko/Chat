package edu.artemiy.chat.messaging.api;

import java.util.UUID;

public interface MessagingService {

    ChatMessage sendMessage(UUID actorUserId, SendMessageCommand command);

    MessageHistoryPage readMessageHistory(UUID actorUserId, ReadMessageHistoryQuery query);

    UnreadMarker advanceReadMarker(UUID actorUserId, AdvanceReadMarkerCommand command);
}
