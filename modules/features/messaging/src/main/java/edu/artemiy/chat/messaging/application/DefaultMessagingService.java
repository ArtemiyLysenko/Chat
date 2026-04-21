package edu.artemiy.chat.messaging.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccess;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessQuery;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessStatus;
import edu.artemiy.chat.contacts.api.DirectMessageEligibility;
import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.messaging.api.AdvanceReadMarkerCommand;
import edu.artemiy.chat.messaging.api.ChatMessageEvent;
import edu.artemiy.chat.messaging.api.ChatUnreadCount;
import edu.artemiy.chat.messaging.api.ChatMessage;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.EditMessageCommand;
import edu.artemiy.chat.messaging.api.MessageAuthor;
import edu.artemiy.chat.messaging.api.MessageEventType;
import edu.artemiy.chat.messaging.api.MessageHistoryPage;
import edu.artemiy.chat.messaging.api.MessageReplyTarget;
import edu.artemiy.chat.messaging.api.MessageState;
import edu.artemiy.chat.messaging.api.MessagingErrorType;
import edu.artemiy.chat.messaging.api.MessagingException;
import edu.artemiy.chat.messaging.api.MessagingService;
import edu.artemiy.chat.messaging.api.ReadMessageHistoryQuery;
import edu.artemiy.chat.messaging.api.SendMessageCommand;
import edu.artemiy.chat.messaging.api.UnreadMarker;
import edu.artemiy.chat.messaging.api.UnreadMarkerUpdatedEvent;
import edu.artemiy.chat.messaging.domain.MessageBodyRules;
import edu.artemiy.chat.messaging.spi.MessagingPersistencePort;
import edu.artemiy.chat.messaging.spi.NewMessageRecord;
import edu.artemiy.chat.messaging.spi.StoredMessage;
import edu.artemiy.chat.messaging.spi.StoredMessageReplyTarget;
import edu.artemiy.chat.messaging.spi.StoredUnreadMarker;
import edu.artemiy.chat.rooms.api.MembershipRole;
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
    private final ApplicationEventPublisher applicationEventPublisher;

    public DefaultMessagingService(
        ClockPort clockPort,
        MessagingPersistencePort messagingPersistencePort,
        RoomMessagingAccessQuery roomMessagingAccessQuery,
        DirectDialogMessagingAccessQuery directDialogMessagingAccessQuery,
        ContactsService contactsService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.clockPort = clockPort;
        this.messagingPersistencePort = messagingPersistencePort;
        this.roomMessagingAccessQuery = roomMessagingAccessQuery;
        this.directDialogMessagingAccessQuery = directDialogMessagingAccessQuery;
        this.contactsService = contactsService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional
    public ChatMessage sendMessage(UUID actorUserId, SendMessageCommand command) {
        ChatTargetRef chat = command.chat();
        String bodyText = MessageBodyRules.requireValid(command.bodyText());
        requireSendAccess(actorUserId, chat);
        StoredMessage replyTarget = command.parentMessageId() == null
            ? null
            : requireMessageInChat(
                command.parentMessageId(),
                chat,
                "messaging.reply_target_invalid",
                "Reply targets must reference a message in the selected chat."
            );

        ChatMessage createdMessage = toChatMessage(messagingPersistencePort.createMessage(new NewMessageRecord(
            UUID.randomUUID(),
            chat,
            actorUserId,
            replyTarget == null ? null : replyTarget.id(),
            bodyText,
            MessageState.ACTIVE,
            clockPort.now()
        )));
        applicationEventPublisher.publishEvent(new ChatMessageEvent(
            actorUserId,
            MessageEventType.CREATED,
            createdMessage,
            createdMessage.createdAt()
        ));
        return createdMessage;
    }

    @Override
    @Transactional
    public ChatMessage editMessage(UUID actorUserId, EditMessageCommand command) {
        String bodyText = MessageBodyRules.requireValid(command.bodyText());
        StoredMessage storedMessage = requireMessage(command.messageId());
        requireChatAccess(actorUserId, storedMessage.chat());
        authorizeEdit(actorUserId, storedMessage);
        if (storedMessage.state() == MessageState.DELETED) {
            throw new MessagingException(
                "messaging.message_edit_deleted",
                "Deleted messages cannot be edited.",
                MessagingErrorType.CONFLICT
            );
        }
        ChatMessage updatedMessage = toChatMessage(messagingPersistencePort.updateMessageBody(storedMessage.id(), bodyText, clockPort.now()));
        applicationEventPublisher.publishEvent(new ChatMessageEvent(
            actorUserId,
            MessageEventType.UPDATED,
            updatedMessage,
            updatedMessage.editedAt()
        ));
        return updatedMessage;
    }

    @Override
    @Transactional
    public ChatMessage deleteMessage(UUID actorUserId, UUID messageId) {
        StoredMessage storedMessage = requireMessage(messageId);
        AuthorizedChat access = requireChatAccess(actorUserId, storedMessage.chat());
        authorizeDelete(actorUserId, access, storedMessage);
        if (storedMessage.state() == MessageState.DELETED) {
            return toChatMessage(storedMessage);
        }
        ChatMessage deletedMessage = toChatMessage(messagingPersistencePort.markMessageDeleted(storedMessage.id(), clockPort.now()));
        applicationEventPublisher.publishEvent(new ChatMessageEvent(
            actorUserId,
            MessageEventType.DELETED,
            deletedMessage,
            clockPort.now()
        ));
        return deletedMessage;
    }

    @Override
    @Transactional(readOnly = true)
    public MessageHistoryPage readMessageHistory(UUID actorUserId, ReadMessageHistoryQuery query) {
        ChatTargetRef chat = query.chat();
        requireChatAccess(actorUserId, chat);
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
        requireChatAccess(actorUserId, chat);
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
        UnreadMarker unreadMarker = toUnreadMarker(savedMarker);
        applicationEventPublisher.publishEvent(new UnreadMarkerUpdatedEvent(
            actorUserId,
            unreadMarker.chat(),
            unreadMarker.lastReadMessageId(),
            unreadMarker.updatedAt()
        ));
        return unreadMarker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatUnreadCount> listUnreadCounts(UUID actorUserId, List<ChatTargetRef> chats) {
        Map<ChatTargetRef, Integer> unreadByChat = new LinkedHashMap<>();
        for (ChatTargetRef chat : List.copyOf(chats)) {
            if (unreadByChat.containsKey(chat)) {
                continue;
            }
            requireChatAccess(actorUserId, chat);
            unreadByChat.put(chat, messagingPersistencePort.countUnreadMessages(actorUserId, chat));
        }
        return unreadByChat.entrySet().stream()
            .map(entry -> new ChatUnreadCount(entry.getKey(), entry.getValue()))
            .toList();
    }

    private AuthorizedChat requireSendAccess(UUID actorUserId, ChatTargetRef chat) {
        AuthorizedChat access = requireChatAccess(actorUserId, chat);
        if (chat.type() == edu.artemiy.chat.messaging.api.ChatTargetType.DIRECT) {
            authorizeDirectSend(actorUserId, access.otherUserId());
        }
        return access;
    }

    private AuthorizedChat requireChatAccess(UUID actorUserId, ChatTargetRef chat) {
        return switch (chat.type()) {
            case ROOM -> authorizeRoom(chat, actorUserId);
            case DIRECT -> authorizeDirectDialog(chat, actorUserId);
        };
    }

    private AuthorizedChat authorizeRoom(ChatTargetRef chat, UUID actorUserId) {
        RoomMessagingAccess access = roomMessagingAccessQuery.evaluateRoomMessagingAccess(actorUserId, chat.id());
        return switch (access.status()) {
            case ALLOWED -> new AuthorizedChat(chat, access.membershipRole(), null);
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
        };
    }

    private AuthorizedChat authorizeDirectDialog(ChatTargetRef chat, UUID actorUserId) {
        DirectDialogMessagingAccess access = directDialogMessagingAccessQuery.evaluateDirectDialogMessagingAccess(actorUserId, chat.id());
        return switch (access.status()) {
            case ALLOWED -> new AuthorizedChat(chat, null, access.otherUserId());
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

    private void authorizeDirectSend(UUID actorUserId, UUID otherUserId) {
        DirectMessageEligibility eligibility = contactsService.evaluateDirectMessageEligibility(actorUserId, otherUserId);
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

    private void authorizeEdit(UUID actorUserId, StoredMessage storedMessage) {
        if (storedMessage.author().id().equals(actorUserId)) {
            return;
        }
        throw new MessagingException(
            "messaging.message_edit_forbidden",
            "Only the message author can edit this message.",
            MessagingErrorType.FORBIDDEN
        );
    }

    private void authorizeDelete(UUID actorUserId, AuthorizedChat access, StoredMessage storedMessage) {
        if (storedMessage.author().id().equals(actorUserId)) {
            return;
        }
        if (storedMessage.chat().type() == edu.artemiy.chat.messaging.api.ChatTargetType.ROOM
            && isModerator(access.roomRole())) {
            return;
        }
        throw new MessagingException(
            "messaging.message_delete_forbidden",
            storedMessage.chat().type() == edu.artemiy.chat.messaging.api.ChatTargetType.ROOM
                ? "Only the message author or a room moderator can delete this room message."
                : "Only the message author can delete this direct message.",
            MessagingErrorType.FORBIDDEN
        );
    }

    private StoredMessage requireMessage(UUID messageId) {
        return messagingPersistencePort.findMessage(messageId).orElseThrow(() -> new MessagingException(
            "messaging.message_not_found",
            "Message was not found.",
            MessagingErrorType.NOT_FOUND
        ));
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

    private static boolean isModerator(MembershipRole roomRole) {
        return roomRole == MembershipRole.OWNER || roomRole == MembershipRole.ADMIN;
    }

    private static ChatMessage toChatMessage(StoredMessage storedMessage) {
        return new ChatMessage(
            storedMessage.id(),
            storedMessage.chat(),
            toMessageAuthor(storedMessage.author()),
            visibleBodyText(storedMessage.bodyText(), storedMessage.state()),
            storedMessage.state(),
            storedMessage.createdAt(),
            storedMessage.editedAt(),
            toReplyTarget(storedMessage.replyTo())
        );
    }

    private static UnreadMarker toUnreadMarker(StoredUnreadMarker storedUnreadMarker) {
        return new UnreadMarker(
            storedUnreadMarker.chat(),
            storedUnreadMarker.lastReadMessageId(),
            storedUnreadMarker.updatedAt()
        );
    }

    private static MessageAuthor toMessageAuthor(edu.artemiy.chat.messaging.spi.StoredMessageAuthor author) {
        return new MessageAuthor(author.id(), author.username(), author.displayName(), author.deleted());
    }

    private static MessageReplyTarget toReplyTarget(StoredMessageReplyTarget replyTarget) {
        if (replyTarget == null) {
            return null;
        }
        return new MessageReplyTarget(
            replyTarget.messageId(),
            toMessageAuthor(replyTarget.author()),
            visibleBodyText(replyTarget.bodyText(), replyTarget.state()),
            replyTarget.state()
        );
    }

    private static String visibleBodyText(String bodyText, MessageState state) {
        return state == MessageState.DELETED ? "" : bodyText;
    }

    private record AuthorizedChat(ChatTargetRef chat, MembershipRole roomRole, UUID otherUserId) {
    }
}
