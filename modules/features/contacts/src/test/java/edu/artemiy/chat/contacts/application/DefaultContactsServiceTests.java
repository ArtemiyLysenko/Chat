package edu.artemiy.chat.contacts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.artemiy.chat.contacts.api.ContactsErrorType;
import edu.artemiy.chat.contacts.api.ContactsException;
import edu.artemiy.chat.contacts.api.CreateFriendRequestCommand;
import edu.artemiy.chat.contacts.api.DirectMessageEligibility;
import edu.artemiy.chat.contacts.api.FriendRequestSubmissionOutcome;
import edu.artemiy.chat.contacts.domain.OrderedContactPair;
import edu.artemiy.chat.contacts.spi.ContactsPersistencePort;
import edu.artemiy.chat.contacts.spi.DuplicateDirectDialogException;
import edu.artemiy.chat.contacts.spi.DuplicateFriendshipException;
import edu.artemiy.chat.contacts.spi.DuplicatePendingFriendRequestException;
import edu.artemiy.chat.contacts.spi.DuplicateUserBlockException;
import edu.artemiy.chat.contacts.spi.FriendshipRequestStatus;
import edu.artemiy.chat.contacts.spi.NewDirectDialogRecord;
import edu.artemiy.chat.contacts.spi.NewFriendshipRecord;
import edu.artemiy.chat.contacts.spi.NewFriendshipRequestRecord;
import edu.artemiy.chat.contacts.spi.NewUserBlockRecord;
import edu.artemiy.chat.contacts.spi.StoredBlockedContactEntry;
import edu.artemiy.chat.contacts.spi.StoredContactUser;
import edu.artemiy.chat.contacts.spi.StoredDirectDialog;
import edu.artemiy.chat.contacts.spi.StoredFriendContactEntry;
import edu.artemiy.chat.contacts.spi.StoredFriendship;
import edu.artemiy.chat.contacts.spi.StoredFriendshipRequest;
import edu.artemiy.chat.contacts.spi.StoredPendingFriendRequestEntry;
import edu.artemiy.chat.contacts.spi.StoredUserBlock;
import edu.artemiy.chat.testing.FixedClock;

class DefaultContactsServiceTests {

    private static final Instant NOW = Instant.parse("2026-04-21T13:00:00Z");

    private final FakeContactsPersistencePort contactsPersistencePort = new FakeContactsPersistencePort();

    private DefaultContactsService service;

    @BeforeEach
    void setUp() {
        service = new DefaultContactsService(new FixedClock(NOW), contactsPersistencePort);
        contactsPersistencePort.addUser(user("captain"));
        contactsPersistencePort.addUser(user("scout"));
        contactsPersistencePort.addUser(user("pilot"));
        contactsPersistencePort.addUser(user("analyst"));
    }

    @Test
    void createsFriendRequestByUsername() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");

        var submission = service.createFriendRequest(captain.id(), new CreateFriendRequestCommand(null, "scout", "Let's connect"));

        assertThat(submission.outcome()).isEqualTo(FriendRequestSubmissionOutcome.REQUEST_CREATED);
        assertThat(submission.requestId()).isNotNull();
        assertThat(contactsPersistencePort.findPendingFriendRequest(captain.id(), contactsPersistencePort.userByUsername("scout").id()))
            .hasValueSatisfying(request -> {
                assertThat(request.messageText()).isEqualTo("Let's connect");
                assertThat(request.status()).isEqualTo(FriendshipRequestStatus.PENDING);
            });
    }

    @Test
    void createsFriendRequestByCompatibilityEquivalentUsername() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");

        var submission = service.createFriendRequest(captain.id(), new CreateFriendRequestCommand(null, "ｓｃｏｕｔ", null));

        assertThat(submission.outcome()).isEqualTo(FriendRequestSubmissionOutcome.REQUEST_CREATED);
        assertThat(submission.user().username()).isEqualTo("scout");
        assertThat(contactsPersistencePort.findPendingFriendRequest(captain.id(), contactsPersistencePort.userByUsername("scout").id()))
            .isPresent();
    }

    @Test
    void createsFriendRequestByUserId() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");

        var submission = service.createFriendRequest(captain.id(), new CreateFriendRequestCommand(scout.id(), null, null));

        assertThat(submission.outcome()).isEqualTo(FriendRequestSubmissionOutcome.REQUEST_CREATED);
        assertThat(submission.user().id()).isEqualTo(scout.id());
        assertThat(contactsPersistencePort.findPendingFriendRequest(captain.id(), scout.id())).isPresent();
    }

    @Test
    void preventsDuplicateSameDirectionPendingRequests() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addPendingRequest(captain.id(), scout.id(), "First ping", NOW.minusSeconds(120));

        assertThatThrownBy(() -> service.createFriendRequest(
            captain.id(),
            new CreateFriendRequestCommand(scout.id(), null, "Second ping")
        )).isInstanceOfSatisfying(ContactsException.class, exception -> {
            assertThat(exception.code()).isEqualTo("contacts.friend_request_pending");
            assertThat(exception.errorType()).isEqualTo(ContactsErrorType.CONFLICT);
        });
    }

    @Test
    void autoAcceptsOppositeDirectionPendingRequest() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        UUID existingRequestId = contactsPersistencePort.addPendingRequest(captain.id(), scout.id(), "Hello", NOW.minusSeconds(90)).id();

        var submission = service.createFriendRequest(scout.id(), new CreateFriendRequestCommand(captain.id(), null, "Reply"));

        assertThat(submission.outcome()).isEqualTo(FriendRequestSubmissionOutcome.AUTO_ACCEPTED);
        assertThat(submission.friendshipId()).isNotNull();
        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isPresent();
        assertThat(contactsPersistencePort.findFriendshipRequest(existingRequestId))
            .hasValueSatisfying(request -> {
                assertThat(request.status()).isEqualTo(FriendshipRequestStatus.ACCEPTED);
                assertThat(request.respondedAt()).isEqualTo(NOW);
            });
        assertThat(contactsPersistencePort.pendingRequests()).isEmpty();
    }

    @Test
    void acceptsPendingRequestExplicitly() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        StoredFriendshipRequest pending = contactsPersistencePort.addPendingRequest(captain.id(), scout.id(), "Hello", NOW.minusSeconds(60));

        service.acceptFriendRequest(scout.id(), pending.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isPresent();
        assertThat(contactsPersistencePort.findFriendshipRequest(pending.id()))
            .hasValueSatisfying(request -> assertThat(request.status()).isEqualTo(FriendshipRequestStatus.ACCEPTED));
    }

    @Test
    void rejectsPendingRequestExplicitly() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        StoredFriendshipRequest pending = contactsPersistencePort.addPendingRequest(captain.id(), scout.id(), "Hello", NOW.minusSeconds(60));

        service.rejectFriendRequest(scout.id(), pending.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isEmpty();
        assertThat(contactsPersistencePort.findFriendshipRequest(pending.id()))
            .hasValueSatisfying(request -> {
                assertThat(request.status()).isEqualTo(FriendshipRequestStatus.REJECTED);
                assertThat(request.respondedAt()).isEqualTo(NOW);
            });
    }

    @Test
    void removesFriendRemovesFriendship() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));

        service.removeFriend(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isEmpty();
    }

    @Test
    void blockRemovesFriendshipIfPresent() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));

        service.blockUser(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isEmpty();
        assertThat(contactsPersistencePort.findUserBlock(captain.id(), scout.id())).isPresent();
    }

    @Test
    void blockWithoutExistingFriendshipStillCreatesABlock() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");

        service.blockUser(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isEmpty();
        assertThat(contactsPersistencePort.findUserBlock(captain.id(), scout.id())).isPresent();
    }

    @Test
    void unblockRemovesTheBlockOnly() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addBlock(captain.id(), scout.id(), NOW.minusSeconds(60));

        service.unblockUser(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findUserBlock(captain.id(), scout.id())).isEmpty();
    }

    @Test
    void unblockDoesNotRestoreFriendship() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addBlock(captain.id(), scout.id(), NOW.minusSeconds(60));

        service.unblockUser(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isEmpty();
    }

    @Test
    void canRemoveDeletedFriend() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));
        contactsPersistencePort.markDeleted(scout.id());

        service.removeFriend(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isEmpty();
    }

    @Test
    void canUnblockDeletedUser() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addBlock(captain.id(), scout.id(), NOW.minusSeconds(60));
        contactsPersistencePort.markDeleted(scout.id());

        service.unblockUser(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findUserBlock(captain.id(), scout.id())).isEmpty();
    }

    @Test
    void deniesFriendRequestCreationWhileActorHasBlockedTarget() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addBlock(captain.id(), scout.id(), NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.createFriendRequest(captain.id(), new CreateFriendRequestCommand(scout.id(), null, null)))
            .isInstanceOfSatisfying(ContactsException.class, exception -> {
                assertThat(exception.code()).isEqualTo("contacts.friend_request_blocked");
                assertThat(exception.errorType()).isEqualTo(ContactsErrorType.CONFLICT);
            });
    }

    @Test
    void deniesFriendRequestCreationWhileTargetHasBlockedActor() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addBlock(scout.id(), captain.id(), NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.createFriendRequest(captain.id(), new CreateFriendRequestCommand(scout.id(), null, null)))
            .isInstanceOfSatisfying(ContactsException.class, exception -> {
                assertThat(exception.code()).isEqualTo("contacts.friend_request_blocked");
                assertThat(exception.errorType()).isEqualTo(ContactsErrorType.CONFLICT);
            });
    }

    @Test
    void directMessageEligibilityReflectsFriendshipPlusNoBlockRules() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");

        assertThat(service.evaluateDirectMessageEligibility(captain.id(), scout.id()))
            .isEqualTo(DirectMessageEligibility.NOT_FRIENDS);

        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));
        assertThat(service.evaluateDirectMessageEligibility(captain.id(), scout.id()))
            .isEqualTo(DirectMessageEligibility.ELIGIBLE);

        contactsPersistencePort.addBlock(captain.id(), scout.id(), NOW.minusSeconds(60));
        assertThat(service.evaluateDirectMessageEligibility(captain.id(), scout.id()))
            .isEqualTo(DirectMessageEligibility.BLOCKED);
    }

    @Test
    void ensuresDirectDialogForEligiblePair() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));

        var dialog = service.ensureDirectDialog(captain.id(), scout.id());

        assertThat(dialog.created()).isTrue();
        assertThat(dialog.participant().username()).isEqualTo("scout");
        assertThat(contactsPersistencePort.findDirectDialogForUsers(captain.id(), scout.id()))
            .hasValueSatisfying(storedDialog -> assertThat(storedDialog.id()).isEqualTo(dialog.dialogId()));
    }

    @Test
    void reusesStableDirectDialogForSamePair() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));

        var firstDialog = service.ensureDirectDialog(captain.id(), scout.id());
        var secondDialog = service.ensureDirectDialog(scout.id(), captain.id());

        assertThat(firstDialog.dialogId()).isEqualTo(secondDialog.dialogId());
        assertThat(secondDialog.created()).isFalse();
        assertThat(contactsPersistencePort.directDialogCount()).isEqualTo(1);
    }

    @Test
    void deniesDirectDialogEnsureWhenPairIsIneligible() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");

        assertThatThrownBy(() -> service.ensureDirectDialog(captain.id(), scout.id()))
            .isInstanceOfSatisfying(ContactsException.class, exception -> {
                assertThat(exception.code()).isEqualTo("contacts.direct_dialog_not_friends");
                assertThat(exception.errorType()).isEqualTo(ContactsErrorType.CONFLICT);
            });
    }

    @Test
    void preservesDirectDialogAfterFriendshipRemoval() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));

        var dialog = service.ensureDirectDialog(captain.id(), scout.id());

        service.removeFriend(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isEmpty();
        assertThat(contactsPersistencePort.findDirectDialogForUsers(captain.id(), scout.id()))
            .hasValueSatisfying(storedDialog -> assertThat(storedDialog.id()).isEqualTo(dialog.dialogId()));
    }

    @Test
    void preservesDirectDialogAfterBlock() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));

        var dialog = service.ensureDirectDialog(captain.id(), scout.id());

        service.blockUser(captain.id(), scout.id());

        assertThat(contactsPersistencePort.findFriendshipForUsers(captain.id(), scout.id())).isEmpty();
        assertThat(contactsPersistencePort.findUserBlock(captain.id(), scout.id())).isPresent();
        assertThat(contactsPersistencePort.findDirectDialogForUsers(captain.id(), scout.id()))
            .hasValueSatisfying(storedDialog -> assertThat(storedDialog.id()).isEqualTo(dialog.dialogId()));
    }

    @Test
    void accountDeletionCleanupRemovesContactStateButPreservesDirectDialog() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        StoredContactUser pilot = contactsPersistencePort.userByUsername("pilot");
        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));
        var dialog = service.ensureDirectDialog(captain.id(), scout.id());
        contactsPersistencePort.addPendingRequest(pilot.id(), captain.id(), "Inbound", NOW.minusSeconds(60));
        contactsPersistencePort.addBlock(captain.id(), pilot.id(), NOW.minusSeconds(30));

        service.handleAccountDeleted(captain.id());

        assertThat(contactsPersistencePort.hasAnyFriendshipInvolving(captain.id())).isFalse();
        assertThat(contactsPersistencePort.hasAnyRequestInvolving(captain.id())).isFalse();
        assertThat(contactsPersistencePort.hasAnyBlockInvolving(captain.id())).isFalse();
        assertThat(contactsPersistencePort.findDirectDialogForUsers(captain.id(), scout.id()))
            .hasValueSatisfying(storedDialog -> assertThat(storedDialog.id()).isEqualTo(dialog.dialogId()));
    }

    @Test
    void listsAcceptedPendingAndBlockedContactState() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        StoredContactUser pilot = contactsPersistencePort.userByUsername("pilot");
        StoredContactUser analyst = contactsPersistencePort.userByUsername("analyst");

        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));
        contactsPersistencePort.addPendingRequest(pilot.id(), captain.id(), "Inbound", NOW.minusSeconds(300));
        contactsPersistencePort.addPendingRequest(captain.id(), analyst.id(), "Outbound", NOW.minusSeconds(120));
        contactsPersistencePort.addBlock(captain.id(), pilot.id(), NOW.minusSeconds(30));
        contactsPersistencePort.rejectPendingFriendRequestsBetween(captain.id(), pilot.id(), NOW.minusSeconds(30));

        var contactsView = service.listContacts(captain.id());

        assertThat(contactsView.friends()).singleElement().satisfies(friend -> {
            assertThat(friend.user().username()).isEqualTo("scout");
            assertThat(friend.friendsSince()).isEqualTo(NOW.minusSeconds(600));
        });
        assertThat(contactsView.inboundPendingRequests()).isEmpty();
        assertThat(contactsView.outboundPendingRequests()).singleElement().satisfies(request -> {
            assertThat(request.user().username()).isEqualTo("analyst");
            assertThat(request.messageText()).isEqualTo("Outbound");
        });
        assertThat(contactsView.blockedUsers()).singleElement().satisfies(blockedUser -> {
            assertThat(blockedUser.user().username()).isEqualTo("pilot");
            assertThat(blockedUser.blockedAt()).isEqualTo(NOW.minusSeconds(30));
        });
    }

    private static StoredContactUser user(String username) {
        return new StoredContactUser(UUID.randomUUID(), username, username, false);
    }

    private static final class FakeContactsPersistencePort implements ContactsPersistencePort {

        private final Map<UUID, StoredContactUser> users = new LinkedHashMap<>();
        private final Map<UUID, StoredFriendshipRequest> friendshipRequests = new LinkedHashMap<>();
        private final Map<UUID, StoredFriendship> friendships = new LinkedHashMap<>();
        private final Map<UUID, StoredUserBlock> userBlocks = new LinkedHashMap<>();
        private final Map<UUID, StoredDirectDialog> directDialogs = new LinkedHashMap<>();

        @Override
        public Optional<StoredContactUser> findUserById(UUID userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Optional<StoredContactUser> findActiveUserById(UUID userId) {
            return Optional.ofNullable(users.get(userId)).filter(user -> !user.deleted());
        }

        @Override
        public Optional<StoredContactUser> findActiveUserByUsername(String username) {
            return users.values().stream()
                .filter(user -> !user.deleted() && user.username().equalsIgnoreCase(username))
                .findFirst();
        }

        @Override
        public void lockUserPair(UUID firstUserId, UUID secondUserId) {
        }

        @Override
        public Optional<StoredFriendship> findFriendship(UUID userLowId, UUID userHighId) {
            return friendships.values().stream()
                .filter(friendship -> friendship.userLowId().equals(userLowId) && friendship.userHighId().equals(userHighId))
                .findFirst();
        }

        @Override
        public Optional<StoredFriendshipRequest> findPendingFriendRequest(UUID requesterUserId, UUID recipientUserId) {
            return friendshipRequests.values().stream()
                .filter(request -> request.requesterUserId().equals(requesterUserId))
                .filter(request -> request.recipientUserId().equals(recipientUserId))
                .filter(request -> request.status() == FriendshipRequestStatus.PENDING)
                .findFirst();
        }

        @Override
        public Optional<StoredFriendshipRequest> findFriendshipRequest(UUID requestId) {
            return Optional.ofNullable(friendshipRequests.get(requestId));
        }

        @Override
        public Optional<StoredFriendshipRequest> findPendingFriendRequestForRecipient(UUID requestId, UUID recipientUserId) {
            return Optional.ofNullable(friendshipRequests.get(requestId))
                .filter(request -> request.recipientUserId().equals(recipientUserId))
                .filter(request -> request.status() == FriendshipRequestStatus.PENDING);
        }

        @Override
        public Optional<StoredUserBlock> findUserBlock(UUID blockerUserId, UUID blockedUserId) {
            return userBlocks.values().stream()
                .filter(block -> block.blockerUserId().equals(blockerUserId))
                .filter(block -> block.blockedUserId().equals(blockedUserId))
                .findFirst();
        }

        @Override
        public Optional<StoredDirectDialog> findDirectDialogById(UUID dialogId) {
            return Optional.ofNullable(directDialogs.get(dialogId));
        }

        @Override
        public Optional<StoredDirectDialog> findDirectDialog(UUID userLowId, UUID userHighId) {
            return directDialogs.values().stream()
                .filter(dialog -> dialog.userLowId().equals(userLowId))
                .filter(dialog -> dialog.userHighId().equals(userHighId))
                .findFirst();
        }

        @Override
        public StoredFriendshipRequest createFriendshipRequest(NewFriendshipRequestRecord request) {
            if (findPendingFriendRequest(request.requesterUserId(), request.recipientUserId()).isPresent()) {
                throw new DuplicatePendingFriendRequestException(null);
            }
            StoredFriendshipRequest stored = new StoredFriendshipRequest(
                request.id(),
                request.requesterUserId(),
                request.recipientUserId(),
                request.messageText(),
                FriendshipRequestStatus.PENDING,
                request.createdAt(),
                null
            );
            friendshipRequests.put(stored.id(), stored);
            return stored;
        }

        @Override
        public StoredFriendship createFriendship(NewFriendshipRecord friendship) {
            if (findFriendship(friendship.userLowId(), friendship.userHighId()).isPresent()) {
                throw new DuplicateFriendshipException(null);
            }
            StoredFriendship stored = new StoredFriendship(
                friendship.id(),
                friendship.userLowId(),
                friendship.userHighId(),
                friendship.createdAt()
            );
            friendships.put(stored.id(), stored);
            return stored;
        }

        @Override
        public StoredUserBlock createUserBlock(NewUserBlockRecord block) {
            if (findUserBlock(block.blockerUserId(), block.blockedUserId()).isPresent()) {
                throw new DuplicateUserBlockException(null);
            }
            StoredUserBlock stored = new StoredUserBlock(
                block.id(),
                block.blockerUserId(),
                block.blockedUserId(),
                block.createdAt()
            );
            userBlocks.put(stored.id(), stored);
            return stored;
        }

        @Override
        public StoredDirectDialog createDirectDialog(NewDirectDialogRecord dialog) {
            if (findDirectDialog(dialog.userLowId(), dialog.userHighId()).isPresent()) {
                throw new DuplicateDirectDialogException(null);
            }
            StoredDirectDialog stored = new StoredDirectDialog(
                dialog.id(),
                dialog.userLowId(),
                dialog.userHighId(),
                dialog.createdAt()
            );
            directDialogs.put(stored.id(), stored);
            return stored;
        }

        @Override
        public void markFriendshipRequestAccepted(UUID requestId, Instant respondedAt) {
            friendshipRequests.computeIfPresent(requestId, (ignored, request) -> new StoredFriendshipRequest(
                request.id(),
                request.requesterUserId(),
                request.recipientUserId(),
                request.messageText(),
                FriendshipRequestStatus.ACCEPTED,
                request.createdAt(),
                respondedAt
            ));
        }

        @Override
        public void markFriendshipRequestRejected(UUID requestId, Instant respondedAt) {
            friendshipRequests.computeIfPresent(requestId, (ignored, request) -> new StoredFriendshipRequest(
                request.id(),
                request.requesterUserId(),
                request.recipientUserId(),
                request.messageText(),
                FriendshipRequestStatus.REJECTED,
                request.createdAt(),
                respondedAt
            ));
        }

        @Override
        public void rejectPendingFriendRequestsBetween(UUID firstUserId, UUID secondUserId, Instant respondedAt) {
            friendshipRequests.replaceAll((ignored, request) -> {
                boolean matchesPair = request.status() == FriendshipRequestStatus.PENDING
                    && ((request.requesterUserId().equals(firstUserId) && request.recipientUserId().equals(secondUserId))
                    || (request.requesterUserId().equals(secondUserId) && request.recipientUserId().equals(firstUserId)));
                if (!matchesPair) {
                    return request;
                }
                return new StoredFriendshipRequest(
                    request.id(),
                    request.requesterUserId(),
                    request.recipientUserId(),
                    request.messageText(),
                    FriendshipRequestStatus.REJECTED,
                    request.createdAt(),
                    respondedAt
                );
            });
        }

        @Override
        public void deleteFriendship(UUID friendshipId) {
            friendships.remove(friendshipId);
        }

        @Override
        public void deleteUserBlock(UUID blockerUserId, UUID blockedUserId) {
            userBlocks.entrySet().removeIf(entry ->
                entry.getValue().blockerUserId().equals(blockerUserId)
                    && entry.getValue().blockedUserId().equals(blockedUserId)
            );
        }

        @Override
        public void deleteRelationshipsForDeletedUser(UUID userId) {
            friendshipRequests.entrySet().removeIf(entry ->
                entry.getValue().requesterUserId().equals(userId) || entry.getValue().recipientUserId().equals(userId)
            );
            friendships.entrySet().removeIf(entry ->
                entry.getValue().userLowId().equals(userId) || entry.getValue().userHighId().equals(userId)
            );
            userBlocks.entrySet().removeIf(entry ->
                entry.getValue().blockerUserId().equals(userId) || entry.getValue().blockedUserId().equals(userId)
            );
        }

        @Override
        public List<StoredFriendContactEntry> listFriends(UUID userId) {
            return friendships.values().stream()
                .filter(friendship -> friendship.userLowId().equals(userId) || friendship.userHighId().equals(userId))
                .map(friendship -> {
                    UUID otherUserId = friendship.userLowId().equals(userId) ? friendship.userHighId() : friendship.userLowId();
                    StoredContactUser otherUser = users.get(otherUserId);
                    return new StoredFriendContactEntry(
                        friendship.id(),
                        otherUser.id(),
                        otherUser.username(),
                        otherUser.displayName(),
                        otherUser.deleted(),
                        friendship.createdAt()
                    );
                })
                .toList();
        }

        @Override
        public List<StoredPendingFriendRequestEntry> listInboundPendingRequests(UUID userId) {
            return friendshipRequests.values().stream()
                .filter(request -> request.recipientUserId().equals(userId))
                .filter(request -> request.status() == FriendshipRequestStatus.PENDING)
                .map(request -> toPendingEntry(request, request.requesterUserId()))
                .toList();
        }

        @Override
        public List<StoredPendingFriendRequestEntry> listOutboundPendingRequests(UUID userId) {
            return friendshipRequests.values().stream()
                .filter(request -> request.requesterUserId().equals(userId))
                .filter(request -> request.status() == FriendshipRequestStatus.PENDING)
                .map(request -> toPendingEntry(request, request.recipientUserId()))
                .toList();
        }

        @Override
        public List<StoredBlockedContactEntry> listBlockedUsers(UUID userId) {
            return userBlocks.values().stream()
                .filter(block -> block.blockerUserId().equals(userId))
                .map(block -> {
                    StoredContactUser otherUser = users.get(block.blockedUserId());
                    return new StoredBlockedContactEntry(
                        otherUser.id(),
                        otherUser.username(),
                        otherUser.displayName(),
                        otherUser.deleted(),
                        block.createdAt()
                    );
                })
                .toList();
        }

        StoredContactUser userByUsername(String username) {
            return findActiveUserByUsername(username).orElseThrow();
        }

        StoredFriendshipRequest addPendingRequest(UUID requesterUserId, UUID recipientUserId, String messageText, Instant createdAt) {
            StoredFriendshipRequest request = new StoredFriendshipRequest(
                UUID.randomUUID(),
                requesterUserId,
                recipientUserId,
                messageText,
                FriendshipRequestStatus.PENDING,
                createdAt,
                null
            );
            friendshipRequests.put(request.id(), request);
            return request;
        }

        StoredFriendship addFriendship(UUID firstUserId, UUID secondUserId, Instant createdAt) {
            OrderedContactPair pair = OrderedContactPair.of(firstUserId, secondUserId);
            StoredFriendship friendship = new StoredFriendship(UUID.randomUUID(), pair.lowUserId(), pair.highUserId(), createdAt);
            friendships.put(friendship.id(), friendship);
            return friendship;
        }

        StoredUserBlock addBlock(UUID blockerUserId, UUID blockedUserId, Instant createdAt) {
            StoredUserBlock block = new StoredUserBlock(UUID.randomUUID(), blockerUserId, blockedUserId, createdAt);
            userBlocks.put(block.id(), block);
            return block;
        }

        Optional<StoredFriendship> findFriendshipForUsers(UUID firstUserId, UUID secondUserId) {
            OrderedContactPair pair = OrderedContactPair.of(firstUserId, secondUserId);
            return findFriendship(pair.lowUserId(), pair.highUserId());
        }

        Optional<StoredDirectDialog> findDirectDialogForUsers(UUID firstUserId, UUID secondUserId) {
            OrderedContactPair pair = OrderedContactPair.of(firstUserId, secondUserId);
            return findDirectDialog(pair.lowUserId(), pair.highUserId());
        }

        List<StoredFriendshipRequest> pendingRequests() {
            return friendshipRequests.values().stream()
                .filter(request -> request.status() == FriendshipRequestStatus.PENDING)
                .toList();
        }

        long directDialogCount() {
            return directDialogs.size();
        }

        boolean hasAnyFriendshipInvolving(UUID userId) {
            return friendships.values().stream()
                .anyMatch(friendship -> friendship.userLowId().equals(userId) || friendship.userHighId().equals(userId));
        }

        boolean hasAnyRequestInvolving(UUID userId) {
            return friendshipRequests.values().stream()
                .anyMatch(request -> request.requesterUserId().equals(userId) || request.recipientUserId().equals(userId));
        }

        boolean hasAnyBlockInvolving(UUID userId) {
            return userBlocks.values().stream()
                .anyMatch(block -> block.blockerUserId().equals(userId) || block.blockedUserId().equals(userId));
        }

        void addUser(StoredContactUser user) {
            users.put(user.id(), user);
        }

        void markDeleted(UUID userId) {
            users.computeIfPresent(userId, (ignored, user) -> new StoredContactUser(
                user.id(),
                user.username(),
                user.displayName(),
                true
            ));
        }

        private StoredPendingFriendRequestEntry toPendingEntry(StoredFriendshipRequest request, UUID otherUserId) {
            StoredContactUser otherUser = users.get(otherUserId);
            return new StoredPendingFriendRequestEntry(
                request.id(),
                otherUser.id(),
                otherUser.username(),
                otherUser.displayName(),
                otherUser.deleted(),
                request.messageText(),
                request.createdAt()
            );
        }
    }
}
