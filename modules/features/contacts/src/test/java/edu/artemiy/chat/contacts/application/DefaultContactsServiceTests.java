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
import edu.artemiy.chat.contacts.api.FriendRequestSubmissionOutcome;
import edu.artemiy.chat.contacts.spi.ContactsPersistencePort;
import edu.artemiy.chat.contacts.spi.DuplicateFriendshipException;
import edu.artemiy.chat.contacts.spi.DuplicatePendingFriendRequestException;
import edu.artemiy.chat.contacts.spi.FriendshipRequestStatus;
import edu.artemiy.chat.contacts.spi.NewFriendshipRecord;
import edu.artemiy.chat.contacts.spi.NewFriendshipRequestRecord;
import edu.artemiy.chat.contacts.spi.StoredContactUser;
import edu.artemiy.chat.contacts.spi.StoredFriendContactEntry;
import edu.artemiy.chat.contacts.spi.StoredFriendship;
import edu.artemiy.chat.contacts.spi.StoredFriendshipRequest;
import edu.artemiy.chat.contacts.spi.StoredPendingFriendRequestEntry;
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
    void listsAcceptedAndPendingContactState() {
        StoredContactUser captain = contactsPersistencePort.userByUsername("captain");
        StoredContactUser scout = contactsPersistencePort.userByUsername("scout");
        StoredContactUser pilot = contactsPersistencePort.userByUsername("pilot");
        StoredContactUser analyst = contactsPersistencePort.userByUsername("analyst");

        contactsPersistencePort.addFriendship(captain.id(), scout.id(), NOW.minusSeconds(600));
        contactsPersistencePort.addPendingRequest(pilot.id(), captain.id(), "Inbound", NOW.minusSeconds(300));
        contactsPersistencePort.addPendingRequest(captain.id(), analyst.id(), "Outbound", NOW.minusSeconds(120));

        var contactsView = service.listContacts(captain.id());

        assertThat(contactsView.friends()).singleElement().satisfies(friend -> {
            assertThat(friend.user().username()).isEqualTo("scout");
            assertThat(friend.friendsSince()).isEqualTo(NOW.minusSeconds(600));
        });
        assertThat(contactsView.inboundPendingRequests()).singleElement().satisfies(request -> {
            assertThat(request.user().username()).isEqualTo("pilot");
            assertThat(request.messageText()).isEqualTo("Inbound");
        });
        assertThat(contactsView.outboundPendingRequests()).singleElement().satisfies(request -> {
            assertThat(request.user().username()).isEqualTo("analyst");
            assertThat(request.messageText()).isEqualTo("Outbound");
        });
        assertThat(contactsView.blockedUsers()).isEmpty();
    }

    private static StoredContactUser user(String username) {
        return new StoredContactUser(UUID.randomUUID(), username, username, false);
    }

    private static final class FakeContactsPersistencePort implements ContactsPersistencePort {

        private final Map<UUID, StoredContactUser> users = new LinkedHashMap<>();
        private final Map<UUID, StoredFriendshipRequest> friendshipRequests = new LinkedHashMap<>();
        private final Map<UUID, StoredFriendship> friendships = new LinkedHashMap<>();

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
            OrderedPair pair = OrderedPair.of(firstUserId, secondUserId);
            StoredFriendship friendship = new StoredFriendship(UUID.randomUUID(), pair.lowUserId(), pair.highUserId(), createdAt);
            friendships.put(friendship.id(), friendship);
            return friendship;
        }

        Optional<StoredFriendship> findFriendshipForUsers(UUID firstUserId, UUID secondUserId) {
            OrderedPair pair = OrderedPair.of(firstUserId, secondUserId);
            return findFriendship(pair.lowUserId(), pair.highUserId());
        }

        List<StoredFriendshipRequest> pendingRequests() {
            return friendshipRequests.values().stream()
                .filter(request -> request.status() == FriendshipRequestStatus.PENDING)
                .toList();
        }

        void addUser(StoredContactUser user) {
            users.put(user.id(), user);
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

        private record OrderedPair(UUID lowUserId, UUID highUserId) {

            private static OrderedPair of(UUID firstUserId, UUID secondUserId) {
                return compareUuid(firstUserId, secondUserId) <= 0
                    ? new OrderedPair(firstUserId, secondUserId)
                    : new OrderedPair(secondUserId, firstUserId);
            }

            private static int compareUuid(UUID firstUserId, UUID secondUserId) {
                int highBitsComparison = Long.compareUnsigned(
                    firstUserId.getMostSignificantBits(),
                    secondUserId.getMostSignificantBits()
                );
                return highBitsComparison != 0
                    ? highBitsComparison
                    : Long.compareUnsigned(firstUserId.getLeastSignificantBits(), secondUserId.getLeastSignificantBits());
            }
        }
    }
}
