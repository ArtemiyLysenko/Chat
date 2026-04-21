package edu.artemiy.chat.messaging.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccess;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessQuery;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessStatus;
import edu.artemiy.chat.contacts.api.DirectMessageEligibility;
import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.messaging.api.AdvanceReadMarkerCommand;
import edu.artemiy.chat.messaging.api.ChatMessage;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.MessageAuthor;
import edu.artemiy.chat.messaging.api.MessageHistoryPage;
import edu.artemiy.chat.messaging.api.MessageState;
import edu.artemiy.chat.messaging.api.MessagingErrorType;
import edu.artemiy.chat.messaging.api.MessagingException;
import edu.artemiy.chat.messaging.api.MessagingService;
import edu.artemiy.chat.messaging.api.ReadMessageHistoryQuery;
import edu.artemiy.chat.messaging.api.SendMessageCommand;
import edu.artemiy.chat.messaging.api.UnreadMarker;
import edu.artemiy.chat.messaging.domain.MessageBodyRules;
import edu.artemiy.chat.messaging.spi.MessagingPersistencePort;
import edu.artemiy.chat.messaging.spi.NewMessageRecord;
import edu.artemiy.chat.messaging.spi.StoredMessage;
import edu.artemiy.chat.messaging.spi.StoredUnreadMarker;
import edu.artemiy.chat.rooms.api.RoomMessagingAccess;
import edu.artemiy.chat.rooms.api.RoomMessagingAccessQuery;
import edu.artemiy.chat.rooms.api.RoomMessagingAccessStatus;

@Service
public class DefaultMessagingService implements MessagingService {

    private final ClockPort clockPort;
    private final MessagingPersistencePort messagingPersistencePort;
    private final RoomMessagingAccessQuery roomMessagingAccessQuery;
    private final DirectDialogMessagingAccessQuery directDialogMessagingAccessQuery;
    private final ContactsService contactsService;

    public DefaultMessagingService(
        ClockPort clockPort,
        MessagingPersistencePort messagingPersistencePort,
        RoomMessagingAccessQuery roomMessagingAccessQuery,
        DirectDialogMessagingAccessQuery directDialogMessagingAccessQuery,
        ContactsService contactsService
    ) {
        this.clockPort = clockPort;
        this.messagingPersistencePort = messagingPersistencePort;
        this.roomMessagingAccessQuery = roomMessagingAccessQuery;
        this.directDialogMessagingAccessQuery = directDialogMessagingAccessQuery;
        this.contactsService = contactsService;
    }

    @Override
    @Transactional
    public ChatMessage sendMessage(UUID actorUserId, SendMessageCommand command) {
        ChatTargetRef chat = command.chat();
        String bodyText = MessageBodyRules.requireValid(command.bodyText());
        authorizeSend(actorUserId, chat);

        StoredMessage storedMessage = messagingPersistencePort.createMessage(new NewMessageRecord(
            UUID.randomUUID(),
            chat,
            actorUserId,
            bodyText,
            MessageState.ACTIVE,
            clockPort.now()
        ));
        return toChatMessage(storedMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public MessageHistoryPage readMessageHistory(UUID actorUserId, ReadMessageHistoryQuery query) {
        ChatTargetRef chat = query.chat();
        authorizeHistoryRead(actorUserId, chat);
        int limit = requireValidLimit(query.limit());

        List<StoredMessage> messages = query.beforeMessageId() == null
            ? messagingPersistencePort.listLatestMessages(chat, limit + 1)
            : messagingPersistencePort.listMessagesBefore(
                chat,
                requireMessageInChat(
                    query.beforeMessageId(),
                    chat,
                    "messaging.history_before_invalid",
                    "The before cursor must reference a message in the selected chat."
                ).createdAt(),
                query.beforeMessageId(),
                limit + 1
            );

        boolean hasMore = messages.size() > limit;
        List<StoredMessage> page = hasMore ? messages.subList(0, limit) : messages;
        List<StoredMessage> orderedPage = new ArrayList<>(page);
        orderedPage.sort(DefaultMessagingService::compareMessages);
        UUID nextBeforeMessageId = hasMore && !orderedPage.isEmpty() ? orderedPage.getFirst().id() : null;

        return new MessageHistoryPage(
            chat,
            orderedPage.stream().map(DefaultMessagingService::toChatMessage).toList(),
            nextBeforeMessageId
        );
    }

    @Override
    @Transactional
    public UnreadMarker advanceReadMarker(UUID actorUserId, AdvanceReadMarkerCommand command) {
        ChatTargetRef chat = command.chat();
        authorizeHistoryRead(actorUserId, chat);
        StoredMessage targetMessage = requireMessageInChat(
            command.lastReadMessageId(),
            chat,
            "messaging.read_marker_invalid",
            "Read markers must reference a message in the selected chat."
        );

        StoredUnreadMarker savedMarker = messagingPersistencePort.saveUnreadMarker(
            actorUserId,
            chat,
            targetMessage.id(),
            clockPort.now()
        );
        return toUnreadMarker(savedMarker);
    }

    private void authorizeSend(UUID actorUserId, ChatTargetRef chat) {
        switch (chat.type()) {
            case ROOM -> authorizeRoom(chat, actorUserId);
            case DIRECT -> authorizeDirectSend(actorUserId, authorizeDirectDialog(chat, actorUserId));
        }
    }

    private void authorizeHistoryRead(UUID actorUserId, ChatTargetRef chat) {
        switch (chat.type()) {
            case ROOM -> authorizeRoom(chat, actorUserId);
            case DIRECT -> authorizeDirectDialog(chat, actorUserId);
        }
    }

    private void authorizeRoom(ChatTargetRef chat, UUID actorUserId) {
        RoomMessagingAccess access = roomMessagingAccessQuery.evaluateRoomMessagingAccess(actorUserId, chat.id());
        switch (access.status()) {
            case ALLOWED -> {
                return;
            }
            case ROOM_NOT_FOUND -> throw new MessagingException(
                "messaging.room_not_found",
                "Room was not found.",
                MessagingErrorType.NOT_FOUND
            );
            case NOT_MEMBER -> throw new MessagingException(
                "messaging.room_membership_required",
                "Current room membership is required.",
                MessagingErrorType.FORBIDDEN
            );
            case BANNED -> throw new MessagingException(
                "messaging.room_banned",
                "Banned users cannot access room messages.",
                MessagingErrorType.FORBIDDEN
            );
        }
    }

    private DirectDialogMessagingAccess authorizeDirectDialog(ChatTargetRef chat, UUID actorUserId) {
        DirectDialogMessagingAccess access = directDialogMessagingAccessQuery.evaluateDirectDialogMessagingAccess(actorUserId, chat.id());
        return switch (access.status()) {
            case ALLOWED -> access;
            case DIRECT_DIALOG_NOT_FOUND -> throw new MessagingException(
                "messaging.direct_dialog_not_found",
                "Direct dialog was not found.",
                MessagingErrorType.NOT_FOUND
            );
            case NOT_PARTICIPANT -> throw new MessagingException(
                "messaging.direct_dialog_forbidden",
                "Only dialog participants can access direct-message history.",
                MessagingErrorType.FORBIDDEN
            );
        };
    }

    private void authorizeDirectSend(UUID actorUserId, DirectDialogMessagingAccess access) {
        DirectMessageEligibility eligibility = contactsService.evaluateDirectMessageEligibility(actorUserId, access.otherUserId());
        switch (eligibility) {
            case ELIGIBLE -> {
                return;
            }
            case NOT_FRIENDS -> throw new MessagingException(
                "messaging.direct_dialog_not_friends",
                "Direct messages require an active friendship.",
                MessagingErrorType.FORBIDDEN
            );
            case BLOCKED -> throw new MessagingException(
                "messaging.direct_dialog_blocked",
                "Direct messages are unavailable while a block exists between these users.",
                MessagingErrorType.FORBIDDEN
            );
        }
    }

    private StoredMessage requireMessageInChat(UUID messageId, ChatTargetRef chat, String code, String message) {
        StoredMessage storedMessage = messagingPersistencePort.findMessage(messageId).orElse(null);
        if (storedMessage == null || !storedMessage.chat().equals(chat)) {
            throw new MessagingException(code, message, MessagingErrorType.BAD_REQUEST);
        }
        return storedMessage;
    }

    private static int requireValidLimit(int limit) {
        if (limit < 1 || limit > ReadMessageHistoryQuery.MAX_LIMIT) {
            throw new MessagingException(
                "messaging.history_limit_invalid",
                "History page limit must be between 1 and %d.".formatted(ReadMessageHistoryQuery.MAX_LIMIT),
                MessagingErrorType.BAD_REQUEST
            );
        }
        return limit;
    }

    private static int compareMessages(StoredMessage left, StoredMessage right) {
        int createdAtComparison = left.createdAt().compareTo(right.createdAt());
        return createdAtComparison != 0 ? createdAtComparison : compareUuid(left.id(), right.id());
    }

    private static int compareUuid(UUID left, UUID right) {
        int highBitsComparison = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return highBitsComparison != 0
            ? highBitsComparison
            : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    private static ChatMessage toChatMessage(StoredMessage storedMessage) {
        return new ChatMessage(
            storedMessage.id(),
            storedMessage.chat(),
            new MessageAuthor(
                storedMessage.author().id(),
                storedMessage.author().username(),
                storedMessage.author().displayName(),
                storedMessage.author().deleted()
            ),
            storedMessage.bodyText(),
            storedMessage.state(),
            storedMessage.createdAt()
        );
    }

    private static UnreadMarker toUnreadMarker(StoredUnreadMarker storedUnreadMarker) {
        return new UnreadMarker(
            storedUnreadMarker.chat(),
            storedUnreadMarker.lastReadMessageId(),
            storedUnreadMarker.updatedAt()
        );
    }
}
