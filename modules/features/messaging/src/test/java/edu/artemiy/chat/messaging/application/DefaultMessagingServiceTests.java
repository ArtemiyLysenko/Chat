package edu.artemiy.chat.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.artemiy.chat.contacts.api.BlockedContactSummary;
import edu.artemiy.chat.contacts.api.ContactUserSummary;
import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.ContactsView;
import edu.artemiy.chat.contacts.api.CreateFriendRequestCommand;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccess;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessQuery;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessStatus;
import edu.artemiy.chat.contacts.api.DirectDialogSummary;
import edu.artemiy.chat.contacts.api.DirectMessageEligibility;
import edu.artemiy.chat.contacts.api.FriendContactSummary;
import edu.artemiy.chat.contacts.api.FriendRequestSubmission;
import edu.artemiy.chat.contacts.api.PendingFriendRequestSummary;
import edu.artemiy.chat.messaging.api.AdvanceReadMarkerCommand;
import edu.artemiy.chat.messaging.api.ChatMessage;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.EditMessageCommand;
import edu.artemiy.chat.messaging.api.MessageHistoryPage;
import edu.artemiy.chat.messaging.api.MessageState;
import edu.artemiy.chat.messaging.api.MessagingErrorType;
import edu.artemiy.chat.messaging.api.MessagingException;
import edu.artemiy.chat.messaging.api.ReadMessageHistoryQuery;
import edu.artemiy.chat.messaging.api.SendMessageCommand;
import edu.artemiy.chat.messaging.api.UnreadMarker;
import edu.artemiy.chat.messaging.spi.MessagingPersistencePort;
import edu.artemiy.chat.messaging.spi.NewMessageRecord;
import edu.artemiy.chat.messaging.spi.StoredMessage;
import edu.artemiy.chat.messaging.spi.StoredMessageAuthor;
import edu.artemiy.chat.messaging.spi.StoredMessageReplyTarget;
import edu.artemiy.chat.messaging.spi.StoredUnreadMarker;
import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.RoomMessagingAccess;
import edu.artemiy.chat.rooms.api.RoomMessagingAccessQuery;
import edu.artemiy.chat.rooms.api.RoomMessagingAccessStatus;
import edu.artemiy.chat.testing.FixedClock;

class DefaultMessagingServiceTests {

    private static final Instant NOW = Instant.parse("2026-04-21T15:00:00Z");
    private static final UUID CAPTAIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SCOUT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TOMBSTONED_ID = UUID.fromString("33333333-3333-3333-3333-333333333334");
    private static final UUID ROOM_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DIRECT_DIALOG_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final FakeMessagingPersistencePort messagingPersistencePort = new FakeMessagingPersistencePort();
    private final FakeRoomMessagingAccessQuery roomMessagingAccessQuery = new FakeRoomMessagingAccessQuery();
    private final FakeDirectDialogMessagingAccessQuery directDialogMessagingAccessQuery = new FakeDirectDialogMessagingAccessQuery();
    private final FakeContactsService contactsService = new FakeContactsService();

    private DefaultMessagingService service;

    @BeforeEach
    void setUp() {
        service = new DefaultMessagingService(
            new FixedClock(NOW),
            messagingPersistencePort,
            roomMessagingAccessQuery,
            directDialogMessagingAccessQuery,
            contactsService,
            event -> {
            }
        );

        messagingPersistencePort.addAuthor(new StoredMessageAuthor(CAPTAIN_ID, "captain", "Captain", false));
        messagingPersistencePort.addAuthor(new StoredMessageAuthor(SCOUT_ID, "scout", "Scout", false));
        messagingPersistencePort.addAuthor(new StoredMessageAuthor(
            TOMBSTONED_ID,
            "deleted-33333333-3333-3333-3333-333333333334",
            "Deleted user",
            true
        ));
        roomMessagingAccessQuery.allow(ROOM_ID, MembershipRole.MEMBER);
        directDialogMessagingAccessQuery.allow(DIRECT_DIALOG_ID, SCOUT_ID);
        contactsService.setEligibility(CAPTAIN_ID, SCOUT_ID, DirectMessageEligibility.ELIGIBLE);
        contactsService.setEligibility(SCOUT_ID, CAPTAIN_ID, DirectMessageEligibility.ELIGIBLE);
    }

    @Test
    void sendsRoomMessageWhenCurrentMemberAndNotBanned() {
        ChatMessage sentMessage = service.sendMessage(CAPTAIN_ID, new SendMessageCommand(roomChat(), "Ready for launch"));

        assertThat(sentMessage.chat()).isEqualTo(roomChat());
        assertThat(sentMessage.author().username()).isEqualTo("captain");
        assertThat(sentMessage.bodyText()).isEqualTo("Ready for launch");
        assertThat(sentMessage.state()).isEqualTo(MessageState.ACTIVE);
        assertThat(sentMessage.createdAt()).isEqualTo(NOW);
        assertThat(sentMessage.replyTo()).isNull();
        assertThat(messagingPersistencePort.messagesForChat(roomChat())).hasSize(1);
    }

    @Test
    void sendsReplyWhenParentMessageBelongsToSameChat() {
        StoredMessage parent = seedMessage(
            roomChat(),
            CAPTAIN_ID,
            "Original",
            MessageState.ACTIVE,
            NOW.minusSeconds(30),
            null,
            null,
            "01000000-0000-0000-0000-000000000001"
        );

        ChatMessage sentMessage = service.sendMessage(
            SCOUT_ID,
            new SendMessageCommand(roomChat(), "Reply", parent.id())
        );

        assertThat(sentMessage.replyTo()).isNotNull();
        assertThat(sentMessage.replyTo().messageId()).isEqualTo(parent.id());
        assertThat(sentMessage.replyTo().author().username()).isEqualTo("captain");
        assertThat(sentMessage.replyTo().bodyText()).isEqualTo("Original");
    }

    @Test
    void deniesReplyWhenParentMessageBelongsToDifferentChat() {
        StoredMessage crossChatParent = seedMessage(
            directChat(),
            CAPTAIN_ID,
            "Elsewhere",
            MessageState.ACTIVE,
            NOW.minusSeconds(30),
            null,
            null,
            "02000000-0000-0000-0000-000000000001"
        );

        assertThatThrownBy(() -> service.sendMessage(
            CAPTAIN_ID,
            new SendMessageCommand(roomChat(), "Wrong reply", crossChatParent.id())
        )).isInstanceOfSatisfying(MessagingException.class, exception -> {
            assertThat(exception.code()).isEqualTo("messaging.reply_target_invalid");
            assertThat(exception.errorType()).isEqualTo(MessagingErrorType.BAD_REQUEST);
        });
    }

    @Test
    void deniesRoomSendWhenUserIsBanned() {
        roomMessagingAccessQuery.banned(ROOM_ID);

        assertThatThrownBy(() -> service.sendMessage(CAPTAIN_ID, new SendMessageCommand(roomChat(), "Should not send")))
            .isInstanceOfSatisfying(MessagingException.class, exception -> {
                assertThat(exception.code()).isEqualTo("messaging.room_banned");
                assertThat(exception.errorType()).isEqualTo(MessagingErrorType.FORBIDDEN);
            });
    }

    @Test
    void deniesDirectDialogSendWhenPairIsBlocked() {
        contactsService.setEligibility(CAPTAIN_ID, SCOUT_ID, DirectMessageEligibility.BLOCKED);

        assertThatThrownBy(() -> service.sendMessage(CAPTAIN_ID, new SendMessageCommand(directChat(), "Blocked")))
            .isInstanceOfSatisfying(MessagingException.class, exception -> {
                assertThat(exception.code()).isEqualTo("messaging.direct_dialog_blocked");
                assertThat(exception.errorType()).isEqualTo(MessagingErrorType.FORBIDDEN);
            });
    }

    @Test
    void onlyAuthorCanEditMessage() {
        StoredMessage message = seedMessage(
            roomChat(),
            CAPTAIN_ID,
            "Draft",
            MessageState.ACTIVE,
            NOW.minusSeconds(30),
            null,
            null,
            "03000000-0000-0000-0000-000000000001"
        );

        assertThatThrownBy(() -> service.editMessage(SCOUT_ID, new EditMessageCommand(message.id(), "Changed")))
            .isInstanceOfSatisfying(MessagingException.class, exception -> {
                assertThat(exception.code()).isEqualTo("messaging.message_edit_forbidden");
                assertThat(exception.errorType()).isEqualTo(MessagingErrorType.FORBIDDEN);
            });
    }

    @Test
    void editingPreservesRowIdentityAndMarksMessageEdited() {
        StoredMessage message = seedMessage(
            roomChat(),
            CAPTAIN_ID,
            "Draft",
            MessageState.ACTIVE,
            NOW.minusSeconds(30),
            null,
            null,
            "04000000-0000-0000-0000-000000000001"
        );

        ChatMessage edited = service.editMessage(CAPTAIN_ID, new EditMessageCommand(message.id(), "Updated"));

        assertThat(edited.id()).isEqualTo(message.id());
        assertThat(edited.state()).isEqualTo(MessageState.EDITED);
        assertThat(edited.bodyText()).isEqualTo("Updated");
        assertThat(edited.createdAt()).isEqualTo(message.createdAt());
        assertThat(edited.editedAt()).isEqualTo(NOW);
        assertThat(messagingPersistencePort.findMessage(message.id()))
            .hasValueSatisfying(stored -> {
                assertThat(stored.state()).isEqualTo(MessageState.EDITED);
                assertThat(stored.editedAt()).isEqualTo(NOW);
                assertThat(stored.bodyText()).isEqualTo("Updated");
            });
    }

    @Test
    void roomHistoryReadRequiresCurrentMembership() {
        roomMessagingAccessQuery.notMember(ROOM_ID);

        assertThatThrownBy(() -> service.readMessageHistory(
            CAPTAIN_ID,
            new ReadMessageHistoryQuery(roomChat(), null, 20)
        )).isInstanceOfSatisfying(MessagingException.class, exception -> {
            assertThat(exception.code()).isEqualTo("messaging.room_membership_required");
            assertThat(exception.errorType()).isEqualTo(MessagingErrorType.FORBIDDEN);
        });
    }

    @Test
    void directDialogHistoryReadRequiresParticipant() {
        directDialogMessagingAccessQuery.notParticipant(DIRECT_DIALOG_ID);

        assertThatThrownBy(() -> service.readMessageHistory(
            CAPTAIN_ID,
            new ReadMessageHistoryQuery(directChat(), null, 20)
        )).isInstanceOfSatisfying(MessagingException.class, exception -> {
            assertThat(exception.code()).isEqualTo("messaging.direct_dialog_forbidden");
            assertThat(exception.errorType()).isEqualTo(MessagingErrorType.FORBIDDEN);
        });
    }

    @Test
    void newestPageDefaultHistoryLoadReturnsNewestMessages() {
        seedMessage(roomChat(), CAPTAIN_ID, "first", MessageState.ACTIVE, NOW.minusSeconds(40), null, null, "10000000-0000-0000-0000-000000000001");
        seedMessage(roomChat(), CAPTAIN_ID, "second", MessageState.ACTIVE, NOW.minusSeconds(30), null, null, "10000000-0000-0000-0000-000000000002");
        seedMessage(roomChat(), CAPTAIN_ID, "third", MessageState.ACTIVE, NOW.minusSeconds(20), null, null, "10000000-0000-0000-0000-000000000003");
        seedMessage(roomChat(), CAPTAIN_ID, "fourth", MessageState.ACTIVE, NOW.minusSeconds(10), null, null, "10000000-0000-0000-0000-000000000004");

        MessageHistoryPage page = service.readMessageHistory(CAPTAIN_ID, new ReadMessageHistoryQuery(roomChat(), null, 2));

        assertThat(page.items()).extracting(ChatMessage::bodyText).containsExactly("third", "fourth");
        assertThat(page.nextBeforeMessageId()).isEqualTo(page.items().getFirst().id());
    }

    @Test
    void beforeCursorLoadsOlderMessages() {
        seedMessage(roomChat(), CAPTAIN_ID, "first", MessageState.ACTIVE, NOW.minusSeconds(40), null, null, "20000000-0000-0000-0000-000000000001");
        seedMessage(roomChat(), CAPTAIN_ID, "second", MessageState.ACTIVE, NOW.minusSeconds(30), null, null, "20000000-0000-0000-0000-000000000002");
        seedMessage(roomChat(), CAPTAIN_ID, "third", MessageState.ACTIVE, NOW.minusSeconds(20), null, null, "20000000-0000-0000-0000-000000000003");
        seedMessage(roomChat(), CAPTAIN_ID, "fourth", MessageState.ACTIVE, NOW.minusSeconds(10), null, null, "20000000-0000-0000-0000-000000000004");

        MessageHistoryPage newestPage = service.readMessageHistory(CAPTAIN_ID, new ReadMessageHistoryQuery(roomChat(), null, 2));
        MessageHistoryPage olderPage = service.readMessageHistory(
            CAPTAIN_ID,
            new ReadMessageHistoryQuery(roomChat(), newestPage.nextBeforeMessageId(), 2)
        );

        assertThat(olderPage.items()).extracting(ChatMessage::bodyText).containsExactly("first", "second");
        assertThat(olderPage.nextBeforeMessageId()).isNull();
    }

    @Test
    void returnsMessagesInChronologicalOrderWithinPage() {
        seedMessage(directChat(), CAPTAIN_ID, "oldest", MessageState.ACTIVE, NOW.minusSeconds(30), null, null, "30000000-0000-0000-0000-000000000001");
        seedMessage(directChat(), SCOUT_ID, "middle", MessageState.EDITED, NOW.minusSeconds(20), NOW.minusSeconds(5), null, "30000000-0000-0000-0000-000000000002");
        seedMessage(directChat(), CAPTAIN_ID, "newest", MessageState.DELETED, NOW.minusSeconds(10), null, null, "30000000-0000-0000-0000-000000000003");

        MessageHistoryPage page = service.readMessageHistory(CAPTAIN_ID, new ReadMessageHistoryQuery(directChat(), null, 3));

        assertThat(page.items()).extracting(ChatMessage::createdAt).isSorted();
        assertThat(page.items()).extracting(ChatMessage::state).containsExactly(
            MessageState.ACTIVE,
            MessageState.EDITED,
            MessageState.DELETED
        );
        assertThat(page.items().get(2).bodyText()).isEmpty();
    }

    @Test
    void sameTimestampPagingUsesPostgresUuidOrdering() {
        Instant sharedTimestamp = NOW.minusSeconds(15);
        seedMessage(roomChat(), CAPTAIN_ID, "six", MessageState.ACTIVE, sharedTimestamp, null, null, "60000000-0000-0000-0000-000000000001");
        StoredMessage seven = seedMessage(roomChat(), CAPTAIN_ID, "seven", MessageState.ACTIVE, sharedTimestamp, null, null, "70000000-0000-0000-0000-000000000001");
        seedMessage(roomChat(), CAPTAIN_ID, "eight", MessageState.ACTIVE, sharedTimestamp, null, null, "80000000-0000-0000-0000-000000000001");

        MessageHistoryPage newestPage = service.readMessageHistory(CAPTAIN_ID, new ReadMessageHistoryQuery(roomChat(), null, 2));
        MessageHistoryPage olderPage = service.readMessageHistory(
            CAPTAIN_ID,
            new ReadMessageHistoryQuery(roomChat(), newestPage.nextBeforeMessageId(), 2)
        );

        assertThat(newestPage.items()).extracting(ChatMessage::bodyText).containsExactly("seven", "eight");
        assertThat(newestPage.nextBeforeMessageId()).isEqualTo(seven.id());
        assertThat(olderPage.items()).extracting(ChatMessage::bodyText).containsExactly("six");
    }

    @Test
    void readMarkerAdvancesOnlyForward() {
        StoredMessage first = seedMessage(roomChat(), CAPTAIN_ID, "first", MessageState.ACTIVE, NOW.minusSeconds(30), null, null, "40000000-0000-0000-0000-000000000001");
        StoredMessage second = seedMessage(roomChat(), CAPTAIN_ID, "second", MessageState.ACTIVE, NOW.minusSeconds(20), null, null, "40000000-0000-0000-0000-000000000002");
        StoredMessage third = seedMessage(roomChat(), CAPTAIN_ID, "third", MessageState.ACTIVE, NOW.minusSeconds(10), null, null, "40000000-0000-0000-0000-000000000003");
        messagingPersistencePort.saveUnreadMarker(CAPTAIN_ID, roomChat(), second.id(), NOW.minusSeconds(5));

        UnreadMarker unchanged = service.advanceReadMarker(
            CAPTAIN_ID,
            new AdvanceReadMarkerCommand(roomChat(), first.id())
        );
        UnreadMarker advanced = service.advanceReadMarker(
            CAPTAIN_ID,
            new AdvanceReadMarkerCommand(roomChat(), third.id())
        );

        assertThat(unchanged.lastReadMessageId()).isEqualTo(second.id());
        assertThat(advanced.lastReadMessageId()).isEqualTo(third.id());
        assertThat(messagingPersistencePort.findUnreadMarker(CAPTAIN_ID, roomChat()))
            .hasValueSatisfying(marker -> assertThat(marker.lastReadMessageId()).isEqualTo(third.id()));
    }

    @Test
    void directDialogHistoryRemainsReadableAfterSendEligibilityLoss() {
        seedMessage(directChat(), CAPTAIN_ID, "history", MessageState.ACTIVE, NOW.minusSeconds(10), null, null, "50000000-0000-0000-0000-000000000001");
        contactsService.setEligibility(CAPTAIN_ID, SCOUT_ID, DirectMessageEligibility.NOT_FRIENDS);

        MessageHistoryPage page = service.readMessageHistory(CAPTAIN_ID, new ReadMessageHistoryQuery(directChat(), null, 10));

        assertThat(page.items()).extracting(ChatMessage::bodyText).containsExactly("history");
    }

    @Test
    void directDialogDeleteIsAuthorOnly() {
        StoredMessage message = seedMessage(
            directChat(),
            SCOUT_ID,
            "Hands off",
            MessageState.ACTIVE,
            NOW.minusSeconds(30),
            null,
            null,
            "51000000-0000-0000-0000-000000000001"
        );

        assertThatThrownBy(() -> service.deleteMessage(CAPTAIN_ID, message.id()))
            .isInstanceOfSatisfying(MessagingException.class, exception -> {
                assertThat(exception.code()).isEqualTo("messaging.message_delete_forbidden");
                assertThat(exception.errorType()).isEqualTo(MessagingErrorType.FORBIDDEN);
            });
    }

    @Test
    void roomDeleteAllowsModeratorForOtherAuthors() {
        roomMessagingAccessQuery.allow(ROOM_ID, MembershipRole.ADMIN);
        StoredMessage message = seedMessage(
            roomChat(),
            SCOUT_ID,
            "Please remove",
            MessageState.ACTIVE,
            NOW.minusSeconds(30),
            null,
            null,
            "52000000-0000-0000-0000-000000000001"
        );

        ChatMessage deleted = service.deleteMessage(CAPTAIN_ID, message.id());

        assertThat(deleted.state()).isEqualTo(MessageState.DELETED);
        assertThat(deleted.bodyText()).isEmpty();
    }

    @Test
    void deletingMessageMarksItDeletedInsteadOfRemovingIt() {
        StoredMessage message = seedMessage(
            roomChat(),
            CAPTAIN_ID,
            "To delete",
            MessageState.ACTIVE,
            NOW.minusSeconds(30),
            null,
            null,
            "53000000-0000-0000-0000-000000000001"
        );

        ChatMessage deleted = service.deleteMessage(CAPTAIN_ID, message.id());

        assertThat(deleted.id()).isEqualTo(message.id());
        assertThat(deleted.state()).isEqualTo(MessageState.DELETED);
        assertThat(deleted.bodyText()).isEmpty();
        assertThat(messagingPersistencePort.findMessage(message.id()))
            .hasValueSatisfying(stored -> assertThat(stored.state()).isEqualTo(MessageState.DELETED));
    }

    @Test
    void historyRenderingPreservesTombstonedAuthorIdentity() {
        seedMessage(
            roomChat(),
            TOMBSTONED_ID,
            "Legacy",
            MessageState.ACTIVE,
            NOW.minusSeconds(30),
            null,
            null,
            "54000000-0000-0000-0000-000000000001"
        );

        MessageHistoryPage page = service.readMessageHistory(CAPTAIN_ID, new ReadMessageHistoryQuery(roomChat(), null, 10));

        assertThat(page.items()).singleElement().satisfies(message -> {
            assertThat(message.author().displayName()).isEqualTo("Deleted user");
            assertThat(message.author().username()).startsWith("deleted-");
            assertThat(message.author().deleted()).isTrue();
        });
    }

    private StoredMessage seedMessage(
        ChatTargetRef chat,
        UUID authorUserId,
        String bodyText,
        MessageState state,
        Instant createdAt,
        Instant editedAt,
        StoredMessageReplyTarget replyTarget,
        String messageId
    ) {
        return messagingPersistencePort.seedMessage(new StoredMessage(
            UUID.fromString(messageId),
            chat,
            messagingPersistencePort.author(authorUserId),
            bodyText,
            state,
            createdAt,
            editedAt,
            replyTarget,
            List.of()
        ));
    }

    private static ChatTargetRef roomChat() {
        return new ChatTargetRef(ChatTargetType.ROOM, ROOM_ID);
    }

    private static ChatTargetRef directChat() {
        return new ChatTargetRef(ChatTargetType.DIRECT, DIRECT_DIALOG_ID);
    }

    private static final class FakeRoomMessagingAccessQuery implements RoomMessagingAccessQuery {

        private final Map<UUID, RoomMessagingAccess> accessByRoomId = new LinkedHashMap<>();

        @Override
        public RoomMessagingAccess evaluateRoomMessagingAccess(UUID actorUserId, UUID roomId) {
            return accessByRoomId.getOrDefault(
                roomId,
                new RoomMessagingAccess(roomId, RoomMessagingAccessStatus.ROOM_NOT_FOUND, null)
            );
        }

        void allow(UUID roomId, MembershipRole membershipRole) {
            accessByRoomId.put(roomId, new RoomMessagingAccess(roomId, RoomMessagingAccessStatus.ALLOWED, membershipRole));
        }

        void notMember(UUID roomId) {
            accessByRoomId.put(roomId, new RoomMessagingAccess(roomId, RoomMessagingAccessStatus.NOT_MEMBER, null));
        }

        void banned(UUID roomId) {
            accessByRoomId.put(roomId, new RoomMessagingAccess(roomId, RoomMessagingAccessStatus.BANNED, null));
        }
    }

    private static final class FakeDirectDialogMessagingAccessQuery implements DirectDialogMessagingAccessQuery {

        private final Map<UUID, DirectDialogMessagingAccess> accessByDialogId = new LinkedHashMap<>();

        @Override
        public DirectDialogMessagingAccess evaluateDirectDialogMessagingAccess(UUID actorUserId, UUID directDialogId) {
            return accessByDialogId.getOrDefault(
                directDialogId,
                new DirectDialogMessagingAccess(
                    directDialogId,
                    DirectDialogMessagingAccessStatus.DIRECT_DIALOG_NOT_FOUND,
                    null
                )
            );
        }

        void allow(UUID directDialogId, UUID otherUserId) {
            accessByDialogId.put(
                directDialogId,
                new DirectDialogMessagingAccess(directDialogId, DirectDialogMessagingAccessStatus.ALLOWED, otherUserId)
            );
        }

        void notParticipant(UUID directDialogId) {
            accessByDialogId.put(
                directDialogId,
                new DirectDialogMessagingAccess(directDialogId, DirectDialogMessagingAccessStatus.NOT_PARTICIPANT, null)
            );
        }
    }

    private static final class FakeContactsService implements ContactsService {

        private final Map<String, DirectMessageEligibility> eligibilityByPair = new LinkedHashMap<>();

        @Override
        public DirectMessageEligibility evaluateDirectMessageEligibility(UUID actorUserId, UUID userId) {
            return eligibilityByPair.getOrDefault(key(actorUserId, userId), DirectMessageEligibility.NOT_FRIENDS);
        }

        void setEligibility(UUID actorUserId, UUID userId, DirectMessageEligibility eligibility) {
            eligibilityByPair.put(key(actorUserId, userId), eligibility);
        }

        private static String key(UUID actorUserId, UUID userId) {
            return actorUserId + ":" + userId;
        }

        @Override
        public ContactsView listContacts(UUID actorUserId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FriendRequestSubmission createFriendRequest(UUID actorUserId, CreateFriendRequestCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void acceptFriendRequest(UUID actorUserId, UUID requestId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rejectFriendRequest(UUID actorUserId, UUID requestId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeFriend(UUID actorUserId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void blockUser(UUID actorUserId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unblockUser(UUID actorUserId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectDialogSummary ensureDirectDialog(UUID actorUserId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void handleAccountDeleted(UUID userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeMessagingPersistencePort implements MessagingPersistencePort {

        private static final Comparator<StoredMessage> DESCENDING_ORDER = FakeMessagingPersistencePort::compareMessagesDescending;

        private final Map<UUID, StoredMessageAuthor> authors = new LinkedHashMap<>();
        private final Map<UUID, StoredMessage> messages = new LinkedHashMap<>();
        private final Map<String, StoredUnreadMarker> unreadMarkers = new LinkedHashMap<>();

        @Override
        public StoredMessage createMessage(NewMessageRecord message) {
            StoredMessage parentMessage = message.parentMessageId() == null ? null : messages.get(message.parentMessageId());
            StoredMessage storedMessage = new StoredMessage(
                message.id(),
                message.chat(),
                author(message.authorUserId()),
                message.bodyText(),
                message.state(),
                message.createdAt(),
                null,
                parentMessage == null
                    ? null
                    : new StoredMessageReplyTarget(
                        parentMessage.id(),
                        parentMessage.author(),
                        parentMessage.bodyText(),
                        parentMessage.state()
                    ),
                List.of()
            );
            messages.put(storedMessage.id(), storedMessage);
            return storedMessage;
        }

        @Override
        public StoredMessage updateMessageBody(UUID messageId, String bodyText, Instant editedAt) {
            StoredMessage existing = Optional.ofNullable(messages.get(messageId)).orElseThrow();
            StoredMessage updated = new StoredMessage(
                existing.id(),
                existing.chat(),
                existing.author(),
                bodyText,
                MessageState.EDITED,
                existing.createdAt(),
                editedAt,
                existing.replyTo(),
                existing.attachments()
            );
            messages.put(messageId, updated);
            return updated;
        }

        @Override
        public StoredMessage markMessageDeleted(UUID messageId, Instant deletedAt) {
            StoredMessage existing = Optional.ofNullable(messages.get(messageId)).orElseThrow();
            StoredMessage deleted = new StoredMessage(
                existing.id(),
                existing.chat(),
                existing.author(),
                existing.bodyText(),
                MessageState.DELETED,
                existing.createdAt(),
                existing.editedAt(),
                existing.replyTo(),
                existing.attachments()
            );
            messages.put(messageId, deleted);
            return deleted;
        }

        @Override
        public Optional<StoredMessage> findMessage(UUID messageId) {
            return Optional.ofNullable(messages.get(messageId));
        }

        @Override
        public List<StoredMessage> listLatestMessages(ChatTargetRef chat, int limit) {
            return messagesForChat(chat).stream().sorted(DESCENDING_ORDER).limit(limit).toList();
        }

        @Override
        public List<StoredMessage> listMessagesBefore(ChatTargetRef chat, Instant beforeCreatedAt, UUID beforeMessageId, int limit) {
            return messagesForChat(chat).stream()
                .filter(message -> message.createdAt().isBefore(beforeCreatedAt)
                    || (message.createdAt().equals(beforeCreatedAt) && compareUuid(message.id(), beforeMessageId) < 0))
                .sorted(DESCENDING_ORDER)
                .limit(limit)
                .toList();
        }

        @Override
        public Optional<StoredUnreadMarker> findUnreadMarker(UUID userId, ChatTargetRef chat) {
            return Optional.ofNullable(unreadMarkers.get(unreadKey(userId, chat)));
        }

        @Override
        public StoredUnreadMarker saveUnreadMarker(UUID userId, ChatTargetRef chat, UUID lastReadMessageId, Instant updatedAt) {
            String unreadKey = unreadKey(userId, chat);
            StoredUnreadMarker existing = unreadMarkers.get(unreadKey);
            if (existing != null) {
                StoredMessage existingMessage = messages.get(existing.lastReadMessageId());
                StoredMessage targetMessage = messages.get(lastReadMessageId);
                if (existingMessage != null && targetMessage != null && compareMessagesAscending(targetMessage, existingMessage) <= 0) {
                    return existing;
                }
            }
            StoredUnreadMarker storedUnreadMarker = new StoredUnreadMarker(
                existing == null ? UUID.randomUUID() : existing.id(),
                userId,
                chat,
                lastReadMessageId,
                updatedAt
            );
            unreadMarkers.put(unreadKey, storedUnreadMarker);
            return storedUnreadMarker;
        }

        @Override
        public int countUnreadMessages(UUID userId, ChatTargetRef chat) {
            StoredUnreadMarker marker = unreadMarkers.get(unreadKey(userId, chat));
            if (marker == null) {
                return messagesForChat(chat).size();
            }
            StoredMessage lastReadMessage = messages.get(marker.lastReadMessageId());
            if (lastReadMessage == null) {
                return messagesForChat(chat).size();
            }
            return Math.toIntExact(messagesForChat(chat).stream()
                .filter(message -> compareMessagesAscending(message, lastReadMessage) > 0)
                .count());
        }

        void addAuthor(StoredMessageAuthor author) {
            authors.put(author.id(), author);
        }

        StoredMessageAuthor author(UUID authorUserId) {
            return Optional.ofNullable(authors.get(authorUserId)).orElseThrow();
        }

        StoredMessage seedMessage(StoredMessage message) {
            messages.put(message.id(), message);
            return message;
        }

        List<StoredMessage> messagesForChat(ChatTargetRef chat) {
            return messages.values().stream()
                .filter(message -> message.chat().equals(chat))
                .toList();
        }

        private static String unreadKey(UUID userId, ChatTargetRef chat) {
            return userId + ":" + chat.type() + ":" + chat.id();
        }

        private static int compareMessagesAscending(StoredMessage left, StoredMessage right) {
            int createdAtComparison = left.createdAt().compareTo(right.createdAt());
            return createdAtComparison != 0 ? createdAtComparison : compareUuid(left.id(), right.id());
        }

        private static int compareMessagesDescending(StoredMessage left, StoredMessage right) {
            return compareMessagesAscending(right, left);
        }

        private static int compareUuid(UUID left, UUID right) {
            int highBitsComparison = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
            return highBitsComparison != 0
                ? highBitsComparison
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
        }
    }
}
