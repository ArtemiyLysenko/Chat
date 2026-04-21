package edu.artemiy.chat.attachments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.artemiy.chat.attachments.api.AttachmentDescriptor;
import edu.artemiy.chat.attachments.api.AttachmentMessagePort;
import edu.artemiy.chat.attachments.api.AttachmentsErrorType;
import edu.artemiy.chat.attachments.api.AttachmentsException;
import edu.artemiy.chat.attachments.api.NewAttachmentMessageRecord;
import edu.artemiy.chat.attachments.api.UploadAttachmentCommand;
import edu.artemiy.chat.attachments.domain.AttachmentUploadRules;
import edu.artemiy.chat.attachments.spi.AttachmentPersistencePort;
import edu.artemiy.chat.attachments.spi.AttachmentStoragePort;
import edu.artemiy.chat.attachments.spi.NewAttachmentRecord;
import edu.artemiy.chat.attachments.spi.NewMessageAttachmentRecord;
import edu.artemiy.chat.attachments.spi.StoredAttachment;
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
import edu.artemiy.chat.messaging.api.ChatMessageEvent;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessageEventType;
import edu.artemiy.chat.messaging.api.MessageState;
import edu.artemiy.chat.messaging.api.ChatMessage;
import edu.artemiy.chat.messaging.api.MessageAttachment;
import edu.artemiy.chat.messaging.api.MessageAuthor;
import edu.artemiy.chat.messaging.api.MessageReplyTarget;
import edu.artemiy.chat.messaging.spi.StoredMessage;
import edu.artemiy.chat.messaging.spi.StoredMessageAttachment;
import edu.artemiy.chat.messaging.spi.StoredMessageAuthor;
import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.RoomMessagingAccess;
import edu.artemiy.chat.rooms.api.RoomMessagingAccessQuery;
import edu.artemiy.chat.rooms.api.RoomMessagingAccessStatus;
import edu.artemiy.chat.testing.FixedClock;

class DefaultAttachmentsServiceTests {

    private static final Instant NOW = Instant.parse("2026-04-21T17:00:00Z");
    private static final UUID CAPTAIN_ID = UUID.fromString("71111111-1111-1111-1111-111111111111");
    private static final UUID SCOUT_ID = UUID.fromString("72222222-2222-2222-2222-222222222222");
    private static final UUID ROOM_ID = UUID.fromString("73333333-3333-3333-3333-333333333333");
    private static final UUID DIRECT_DIALOG_ID = UUID.fromString("74444444-4444-4444-4444-444444444444");

    private final FakeAttachmentPersistencePort attachmentPersistencePort = new FakeAttachmentPersistencePort();
    private final FakeAttachmentStoragePort attachmentStoragePort = new FakeAttachmentStoragePort();
    private final FakeAttachmentMessagePort attachmentMessagePort = new FakeAttachmentMessagePort(attachmentPersistencePort);
    private final FakeRoomMessagingAccessQuery roomMessagingAccessQuery = new FakeRoomMessagingAccessQuery();
    private final FakeDirectDialogMessagingAccessQuery directDialogMessagingAccessQuery = new FakeDirectDialogMessagingAccessQuery();
    private final FakeContactsService contactsService = new FakeContactsService();
    private final List<Object> publishedEvents = new ArrayList<>();

    private DefaultAttachmentsService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAttachmentsService(
            new FixedClock(NOW),
            attachmentPersistencePort,
            attachmentStoragePort,
            attachmentMessagePort,
            roomMessagingAccessQuery,
            directDialogMessagingAccessQuery,
            contactsService,
            publishedEvents::add
        );

        attachmentMessagePort.addAuthor(new StoredMessageAuthor(CAPTAIN_ID, "captain", "Captain", false));
        attachmentMessagePort.addAuthor(new StoredMessageAuthor(SCOUT_ID, "scout", "Scout", false));
        roomMessagingAccessQuery.allow(ROOM_ID, MembershipRole.MEMBER);
        directDialogMessagingAccessQuery.allow(DIRECT_DIALOG_ID, SCOUT_ID);
        contactsService.setEligibility(CAPTAIN_ID, SCOUT_ID, DirectMessageEligibility.ELIGIBLE);
    }

    @Test
    void uploadCreatesMessageBoundAttachmentAndPublishesCreatedEvent() {
        byte[] content = "room attachment".getBytes(StandardCharsets.UTF_8);

        AttachmentDescriptor descriptor = service.uploadAttachment(
            CAPTAIN_ID,
            new UploadAttachmentCommand(roomChat(), "notes.txt", "text/plain", content, "Sprint notes", null)
        );

        assertThat(descriptor.originalName()).isEqualTo("notes.txt");
        assertThat(descriptor.mediaType()).isEqualTo("text/plain");
        assertThat(descriptor.sizeBytes()).isEqualTo(content.length);
        assertThat(descriptor.storageKey()).startsWith("uploads/");
        assertThat(attachmentStoragePort.read(descriptor.storageKey())).containsExactly(content);
        assertThat(attachmentPersistencePort.findAttachment(UUID.fromString(descriptor.id())))
            .hasValueSatisfying(attachment -> {
                assertThat(attachment.chat()).isEqualTo(roomChat());
                assertThat(attachment.originalName()).isEqualTo("notes.txt");
            });
        assertThat(attachmentMessagePort.messagesForChat(roomChat()))
            .singleElement()
            .satisfies(message -> {
                assertThat(message.bodyText()).isEqualTo("Sprint notes");
                assertThat(message.attachments()).singleElement().satisfies(attachment -> {
                    assertThat(attachment.attachmentId()).isEqualTo(UUID.fromString(descriptor.id()));
                    assertThat(attachment.commentText()).isEqualTo("Sprint notes");
                    assertThat(attachment.originalName()).isEqualTo("notes.txt");
                });
            });
        assertThat(publishedEvents).singleElement().isInstanceOfSatisfying(ChatMessageEvent.class, event -> {
            assertThat(event.type()).isEqualTo(MessageEventType.CREATED);
            assertThat(event.message().chat()).isEqualTo(roomChat());
            assertThat(event.message().attachments()).singleElement().satisfies(attachment -> {
                assertThat(attachment.attachmentId()).isEqualTo(UUID.fromString(descriptor.id()));
                assertThat(attachment.originalName()).isEqualTo("notes.txt");
            });
        });
    }

    @Test
    void rejectsFilesAboveTwentyMegabytesBeforePersisting() {
        byte[] content = new byte[(int) AttachmentUploadRules.MAX_FILE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> service.uploadAttachment(
            CAPTAIN_ID,
            new UploadAttachmentCommand(roomChat(), "archive.zip", "application/zip", content, null, null)
        )).isInstanceOfSatisfying(AttachmentsException.class, exception -> {
            assertThat(exception.code()).isEqualTo("attachments.file_too_large");
            assertThat(exception.errorType()).isEqualTo(AttachmentsErrorType.BAD_REQUEST);
        });

        assertThat(attachmentPersistencePort.attachments()).isEmpty();
        assertThat(attachmentMessagePort.messagesForChat(roomChat())).isEmpty();
        assertThat(attachmentStoragePort.isEmpty()).isTrue();
    }

    @Test
    void rejectsImagesAboveThreeMegabytesBeforePersisting() {
        byte[] content = new byte[(int) AttachmentUploadRules.MAX_IMAGE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> service.uploadAttachment(
            CAPTAIN_ID,
            new UploadAttachmentCommand(roomChat(), "large.png", "image/png", content, null, null)
        )).isInstanceOfSatisfying(AttachmentsException.class, exception -> {
            assertThat(exception.code()).isEqualTo("attachments.image_too_large");
            assertThat(exception.errorType()).isEqualTo(AttachmentsErrorType.BAD_REQUEST);
        });

        assertThat(attachmentPersistencePort.attachments()).isEmpty();
        assertThat(attachmentMessagePort.messagesForChat(roomChat())).isEmpty();
        assertThat(attachmentStoragePort.isEmpty()).isTrue();
    }

    @Test
    void readAndDownloadRequireCurrentRoomMembership() {
        StoredAttachment attachment = new StoredAttachment(
            UUID.fromString("75555555-5555-5555-5555-555555555555"),
            "uploads/75555555-5555-5555-5555-555555555555",
            "brief.txt",
            "text/plain",
            5,
            "9d6f965ac832e40a5d1d11c0d2ca5b755aa24a4db46d0116d9781f1922d279fe",
            CAPTAIN_ID,
            roomChat(),
            NOW
        );
        attachmentPersistencePort.seedAttachment(attachment);
        attachmentStoragePort.store(attachment.storageKey(), "brief".getBytes(StandardCharsets.UTF_8));
        roomMessagingAccessQuery.notMember(ROOM_ID);

        assertThatThrownBy(() -> service.readAttachment(CAPTAIN_ID, attachment.id()))
            .isInstanceOfSatisfying(AttachmentsException.class, exception -> {
                assertThat(exception.code()).isEqualTo("attachments.room_membership_required");
                assertThat(exception.errorType()).isEqualTo(AttachmentsErrorType.FORBIDDEN);
            });
        assertThatThrownBy(() -> service.downloadAttachment(CAPTAIN_ID, attachment.id()))
            .isInstanceOfSatisfying(AttachmentsException.class, exception -> {
                assertThat(exception.code()).isEqualTo("attachments.room_membership_required");
                assertThat(exception.errorType()).isEqualTo(AttachmentsErrorType.FORBIDDEN);
            });
    }

    @Test
    void directUploadsRequireCurrentEligibilityToSend() {
        contactsService.setEligibility(CAPTAIN_ID, SCOUT_ID, DirectMessageEligibility.BLOCKED);

        assertThatThrownBy(() -> service.uploadAttachment(
            CAPTAIN_ID,
            new UploadAttachmentCommand(
                directChat(),
                "blocked.txt",
                "text/plain",
                "blocked".getBytes(StandardCharsets.UTF_8),
                null,
                null
            )
        )).isInstanceOfSatisfying(AttachmentsException.class, exception -> {
            assertThat(exception.code()).isEqualTo("attachments.direct_dialog_blocked");
            assertThat(exception.errorType()).isEqualTo(AttachmentsErrorType.FORBIDDEN);
        });
    }

    private static ChatTargetRef roomChat() {
        return new ChatTargetRef(ChatTargetType.ROOM, ROOM_ID);
    }

    private static ChatTargetRef directChat() {
        return new ChatTargetRef(ChatTargetType.DIRECT, DIRECT_DIALOG_ID);
    }

    private static final class FakeAttachmentPersistencePort implements AttachmentPersistencePort {

        private final Map<UUID, StoredAttachment> attachments = new LinkedHashMap<>();
        private final List<NewMessageAttachmentRecord> messageAttachments = new ArrayList<>();

        @Override
        public StoredAttachment createAttachment(NewAttachmentRecord attachment) {
            StoredAttachment stored = new StoredAttachment(
                attachment.id(),
                attachment.storageKey(),
                attachment.originalName(),
                attachment.mediaType(),
                attachment.sizeBytes(),
                attachment.sha256(),
                attachment.uploadedByUserId(),
                attachment.chat(),
                attachment.createdAt()
            );
            attachments.put(stored.id(), stored);
            return stored;
        }

        @Override
        public void attachToMessage(NewMessageAttachmentRecord messageAttachment) {
            messageAttachments.add(messageAttachment);
        }

        @Override
        public Optional<StoredAttachment> findAttachment(UUID attachmentId) {
            return Optional.ofNullable(attachments.get(attachmentId));
        }

        List<StoredAttachment> attachments() {
            return List.copyOf(attachments.values());
        }

        void seedAttachment(StoredAttachment attachment) {
            attachments.put(attachment.id(), attachment);
        }

        List<StoredMessageAttachment> attachmentsForMessage(UUID messageId) {
            return messageAttachments.stream()
                .filter(link -> link.messageId().equals(messageId))
                .sorted(Comparator.comparingInt(NewMessageAttachmentRecord::sortOrder))
                .map(link -> {
                    StoredAttachment attachment = attachments.get(link.attachmentId());
                    return new StoredMessageAttachment(
                        attachment.id(),
                        attachment.originalName(),
                        attachment.mediaType(),
                        attachment.sizeBytes(),
                        link.commentText(),
                        link.sortOrder()
                    );
                })
                .toList();
        }
    }

    private static final class FakeAttachmentStoragePort implements AttachmentStoragePort {

        private final Map<String, byte[]> contentByStorageKey = new HashMap<>();

        @Override
        public void store(String storageKey, byte[] content) {
            contentByStorageKey.put(storageKey, content.clone());
        }

        @Override
        public byte[] read(String storageKey) {
            return contentByStorageKey.get(storageKey).clone();
        }

        @Override
        public void delete(String storageKey) {
            contentByStorageKey.remove(storageKey);
        }

        @Override
        public boolean exists(String storageKey) {
            return contentByStorageKey.containsKey(storageKey);
        }

        boolean isEmpty() {
            return contentByStorageKey.isEmpty();
        }
    }

    private static final class FakeAttachmentMessagePort implements AttachmentMessagePort {

        private final FakeAttachmentPersistencePort attachmentPersistencePort;
        private final Map<UUID, StoredMessageAuthor> authors = new HashMap<>();
        private final Map<UUID, StoredMessage> messages = new LinkedHashMap<>();

        private FakeAttachmentMessagePort(FakeAttachmentPersistencePort attachmentPersistencePort) {
            this.attachmentPersistencePort = attachmentPersistencePort;
        }

        void addAuthor(StoredMessageAuthor author) {
            authors.put(author.id(), author);
        }

        List<ChatMessage> messagesForChat(ChatTargetRef chat) {
            return messages.values().stream()
                .filter(message -> message.chat().equals(chat))
                .map(message -> findMessage(message.id()).orElseThrow())
                .toList();
        }

        @Override
        public void createMessage(NewAttachmentMessageRecord message) {
            StoredMessage stored = new StoredMessage(
                message.id(),
                message.chat(),
                requireAuthor(message.authorUserId()),
                message.bodyText(),
                MessageState.ACTIVE,
                message.createdAt(),
                null,
                null,
                List.of()
            );
            messages.put(stored.id(), stored);
        }

        @Override
        public Optional<ChatMessage> findMessage(UUID messageId) {
            StoredMessage stored = messages.get(messageId);
            if (stored == null) {
                return Optional.empty();
            }
            return Optional.of(new ChatMessage(
                stored.id(),
                stored.chat(),
                new MessageAuthor(
                    stored.author().id(),
                    stored.author().username(),
                    stored.author().displayName(),
                    stored.author().deleted()
                ),
                stored.bodyText(),
                stored.state(),
                stored.createdAt(),
                stored.editedAt(),
                stored.replyTo() == null
                    ? null
                    : new MessageReplyTarget(
                        stored.replyTo().messageId(),
                        new MessageAuthor(
                            stored.replyTo().author().id(),
                            stored.replyTo().author().username(),
                            stored.replyTo().author().displayName(),
                            stored.replyTo().author().deleted()
                        ),
                        stored.replyTo().bodyText(),
                        stored.replyTo().state()
                    ),
                attachmentPersistencePort.attachmentsForMessage(messageId).stream()
                    .map(attachment -> new MessageAttachment(
                        attachment.attachmentId(),
                        attachment.originalName(),
                        attachment.mediaType(),
                        attachment.sizeBytes(),
                        attachment.commentText(),
                        attachment.sortOrder()
                    ))
                    .toList()
            ));
        }

        private StoredMessageAuthor requireAuthor(UUID authorUserId) {
            StoredMessageAuthor author = authors.get(authorUserId);
            if (author == null) {
                throw new IllegalStateException("Missing author " + authorUserId);
            }
            return author;
        }
    }

    private static final class FakeRoomMessagingAccessQuery implements RoomMessagingAccessQuery {

        private final Map<UUID, RoomMessagingAccess> accessByRoomId = new HashMap<>();

        void allow(UUID roomId, MembershipRole membershipRole) {
            accessByRoomId.put(roomId, new RoomMessagingAccess(roomId, RoomMessagingAccessStatus.ALLOWED, membershipRole));
        }

        void notMember(UUID roomId) {
            accessByRoomId.put(roomId, new RoomMessagingAccess(roomId, RoomMessagingAccessStatus.NOT_MEMBER, null));
        }

        @Override
        public RoomMessagingAccess evaluateRoomMessagingAccess(UUID actorUserId, UUID roomId) {
            return accessByRoomId.getOrDefault(roomId, new RoomMessagingAccess(roomId, RoomMessagingAccessStatus.ROOM_NOT_FOUND, null));
        }
    }

    private static final class FakeDirectDialogMessagingAccessQuery implements DirectDialogMessagingAccessQuery {

        private final Map<UUID, DirectDialogMessagingAccess> accessByDialogId = new HashMap<>();

        void allow(UUID dialogId, UUID otherUserId) {
            accessByDialogId.put(dialogId, new DirectDialogMessagingAccess(dialogId, DirectDialogMessagingAccessStatus.ALLOWED, otherUserId));
        }

        @Override
        public DirectDialogMessagingAccess evaluateDirectDialogMessagingAccess(UUID actorUserId, UUID directDialogId) {
            return accessByDialogId.getOrDefault(
                directDialogId,
                new DirectDialogMessagingAccess(directDialogId, DirectDialogMessagingAccessStatus.DIRECT_DIALOG_NOT_FOUND, null)
            );
        }
    }

    private static final class FakeContactsService implements ContactsService {

        private final Map<String, DirectMessageEligibility> eligibilityByPair = new HashMap<>();

        void setEligibility(UUID actorUserId, UUID userId, DirectMessageEligibility eligibility) {
            eligibilityByPair.put(key(actorUserId, userId), eligibility);
        }

        @Override
        public DirectMessageEligibility evaluateDirectMessageEligibility(UUID actorUserId, UUID userId) {
            return eligibilityByPair.getOrDefault(key(actorUserId, userId), DirectMessageEligibility.NOT_FRIENDS);
        }

        @Override
        public ContactsView listContacts(UUID actorUserId) {
            return new ContactsView(
                List.<FriendContactSummary>of(),
                List.<PendingFriendRequestSummary>of(),
                List.<PendingFriendRequestSummary>of(),
                List.<BlockedContactSummary>of()
            );
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

        private static String key(UUID actorUserId, UUID userId) {
            return actorUserId + ":" + userId;
        }
    }
}
