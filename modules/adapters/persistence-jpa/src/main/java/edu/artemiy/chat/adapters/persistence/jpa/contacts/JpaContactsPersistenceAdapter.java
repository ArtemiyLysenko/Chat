package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.contacts.spi.ContactsPersistencePort;
import edu.artemiy.chat.contacts.spi.DuplicateFriendshipException;
import edu.artemiy.chat.contacts.spi.DuplicatePendingFriendRequestException;
import edu.artemiy.chat.contacts.spi.DuplicateUserBlockException;
import edu.artemiy.chat.contacts.spi.FriendshipRequestStatus;
import edu.artemiy.chat.contacts.spi.NewFriendshipRecord;
import edu.artemiy.chat.contacts.spi.NewFriendshipRequestRecord;
import edu.artemiy.chat.contacts.spi.NewUserBlockRecord;
import edu.artemiy.chat.contacts.spi.StoredBlockedContactEntry;
import edu.artemiy.chat.contacts.spi.StoredContactUser;
import edu.artemiy.chat.contacts.spi.StoredFriendContactEntry;
import edu.artemiy.chat.contacts.spi.StoredFriendship;
import edu.artemiy.chat.contacts.spi.StoredFriendshipRequest;
import edu.artemiy.chat.contacts.spi.StoredPendingFriendRequestEntry;
import edu.artemiy.chat.contacts.spi.StoredUserBlock;

@Component
class JpaContactsPersistenceAdapter implements ContactsPersistencePort {

    private final FriendshipRequestJpaRepository friendshipRequestJpaRepository;
    private final FriendshipJpaRepository friendshipJpaRepository;
    private final UserBlockJpaRepository userBlockJpaRepository;
    private final ContactUserReadRepository contactUserReadRepository;
    private final JdbcTemplate jdbcTemplate;

    JpaContactsPersistenceAdapter(
        FriendshipRequestJpaRepository friendshipRequestJpaRepository,
        FriendshipJpaRepository friendshipJpaRepository,
        UserBlockJpaRepository userBlockJpaRepository,
        ContactUserReadRepository contactUserReadRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.friendshipRequestJpaRepository = friendshipRequestJpaRepository;
        this.friendshipJpaRepository = friendshipJpaRepository;
        this.userBlockJpaRepository = userBlockJpaRepository;
        this.contactUserReadRepository = contactUserReadRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StoredContactUser> findUserById(UUID userId) {
        return contactUserReadRepository.findUserById(userId).map(JpaContactsPersistenceAdapter::toStoredUser);
    }

    @Override
    public Optional<StoredContactUser> findActiveUserById(UUID userId) {
        return contactUserReadRepository.findActiveUserById(userId).map(JpaContactsPersistenceAdapter::toStoredUser);
    }

    @Override
    public Optional<StoredContactUser> findActiveUserByUsername(String username) {
        return contactUserReadRepository.findActiveUserByUsername(username).map(JpaContactsPersistenceAdapter::toStoredUser);
    }

    @Override
    public void lockUserPair(UUID firstUserId, UUID secondUserId) {
        jdbcTemplate.queryForList(
            """
                select id
                from users
                where id in (?, ?)
                order by id
                for update
            """,
            UUID.class,
            firstUserId,
            secondUserId
        );
    }

    @Override
    public Optional<StoredFriendship> findFriendship(UUID userLowId, UUID userHighId) {
        return friendshipJpaRepository.findByUserLowIdAndUserHighId(userLowId, userHighId)
            .map(JpaContactsPersistenceAdapter::toStoredFriendship);
    }

    @Override
    public Optional<StoredFriendshipRequest> findPendingFriendRequest(UUID requesterUserId, UUID recipientUserId) {
        return friendshipRequestJpaRepository.findByRequesterUserIdAndRecipientUserIdAndStatus(
            requesterUserId,
            recipientUserId,
            FriendshipRequestStatus.PENDING
        ).map(JpaContactsPersistenceAdapter::toStoredFriendshipRequest);
    }

    @Override
    public Optional<StoredFriendshipRequest> findFriendshipRequest(UUID requestId) {
        return friendshipRequestJpaRepository.findById(requestId).map(JpaContactsPersistenceAdapter::toStoredFriendshipRequest);
    }

    @Override
    public Optional<StoredFriendshipRequest> findPendingFriendRequestForRecipient(UUID requestId, UUID recipientUserId) {
        return friendshipRequestJpaRepository.findByIdAndRecipientUserIdAndStatus(
            requestId,
            recipientUserId,
            FriendshipRequestStatus.PENDING
        ).map(JpaContactsPersistenceAdapter::toStoredFriendshipRequest);
    }

    @Override
    public Optional<StoredUserBlock> findUserBlock(UUID blockerUserId, UUID blockedUserId) {
        return userBlockJpaRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)
            .map(JpaContactsPersistenceAdapter::toStoredUserBlock);
    }

    @Override
    public StoredFriendshipRequest createFriendshipRequest(NewFriendshipRequestRecord request) {
        try {
            return toStoredFriendshipRequest(friendshipRequestJpaRepository.saveAndFlush(new FriendshipRequestEntity(
                request.id(),
                request.requesterUserId(),
                request.recipientUserId(),
                request.messageText(),
                FriendshipRequestStatus.PENDING,
                request.createdAt(),
                null
            )));
        }
        catch (DataIntegrityViolationException exception) {
            if (isConstraint(exception, "uq_friendship_requests_pending_direction")) {
                throw new DuplicatePendingFriendRequestException(exception);
            }
            throw exception;
        }
    }

    @Override
    public StoredFriendship createFriendship(NewFriendshipRecord friendship) {
        try {
            return toStoredFriendship(friendshipJpaRepository.saveAndFlush(new FriendshipEntity(
                friendship.id(),
                friendship.userLowId(),
                friendship.userHighId(),
                friendship.createdAt()
            )));
        }
        catch (DataIntegrityViolationException exception) {
            if (isConstraint(exception, "uq_friendships_pair")) {
                throw new DuplicateFriendshipException(exception);
            }
            throw exception;
        }
    }

    @Override
    public StoredUserBlock createUserBlock(NewUserBlockRecord block) {
        try {
            return toStoredUserBlock(userBlockJpaRepository.saveAndFlush(new UserBlockEntity(
                block.id(),
                block.blockerUserId(),
                block.blockedUserId(),
                block.createdAt()
            )));
        }
        catch (DataIntegrityViolationException exception) {
            if (isConstraint(exception, "uq_user_blocks_direction")) {
                throw new DuplicateUserBlockException(exception);
            }
            throw exception;
        }
    }

    @Override
    public void markFriendshipRequestAccepted(UUID requestId, java.time.Instant respondedAt) {
        FriendshipRequestEntity entity = friendshipRequestJpaRepository.findById(requestId).orElseThrow();
        entity.markAccepted(respondedAt);
        friendshipRequestJpaRepository.saveAndFlush(entity);
    }

    @Override
    public void markFriendshipRequestRejected(UUID requestId, java.time.Instant respondedAt) {
        FriendshipRequestEntity entity = friendshipRequestJpaRepository.findById(requestId).orElseThrow();
        entity.markRejected(respondedAt);
        friendshipRequestJpaRepository.saveAndFlush(entity);
    }

    @Override
    public void rejectPendingFriendRequestsBetween(UUID firstUserId, UUID secondUserId, java.time.Instant respondedAt) {
        friendshipRequestJpaRepository.rejectPendingRequestsBetween(firstUserId, secondUserId, respondedAt);
    }

    @Override
    public void deleteFriendship(UUID friendshipId) {
        friendshipJpaRepository.deleteById(friendshipId);
    }

    @Override
    public void deleteUserBlock(UUID blockerUserId, UUID blockedUserId) {
        userBlockJpaRepository.deleteByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId);
    }

    @Override
    public List<StoredFriendContactEntry> listFriends(UUID userId) {
        return friendshipJpaRepository.findFriendContacts(userId).stream()
            .map(projection -> new StoredFriendContactEntry(
                projection.getFriendshipId(),
                projection.getOtherUserId(),
                projection.getOtherUsername(),
                projection.getOtherDisplayName(),
                projection.getOtherDeletedAt() != null,
                projection.getFriendsSince()
            ))
            .toList();
    }

    @Override
    public List<StoredPendingFriendRequestEntry> listInboundPendingRequests(UUID userId) {
        return friendshipRequestJpaRepository.findInboundPendingRequests(userId).stream()
            .map(JpaContactsPersistenceAdapter::toPendingEntry)
            .toList();
    }

    @Override
    public List<StoredPendingFriendRequestEntry> listOutboundPendingRequests(UUID userId) {
        return friendshipRequestJpaRepository.findOutboundPendingRequests(userId).stream()
            .map(JpaContactsPersistenceAdapter::toPendingEntry)
            .toList();
    }

    @Override
    public List<StoredBlockedContactEntry> listBlockedUsers(UUID userId) {
        return userBlockJpaRepository.findBlockedContacts(userId).stream()
            .map(projection -> new StoredBlockedContactEntry(
                projection.getOtherUserId(),
                projection.getOtherUsername(),
                projection.getOtherDisplayName(),
                projection.getOtherDeletedAt() != null,
                projection.getBlockedAt()
            ))
            .toList();
    }

    private static StoredPendingFriendRequestEntry toPendingEntry(FriendshipRequestJpaRepository.PendingFriendRequestProjection projection) {
        return new StoredPendingFriendRequestEntry(
            projection.getRequestId(),
            projection.getOtherUserId(),
            projection.getOtherUsername(),
            projection.getOtherDisplayName(),
            projection.getOtherDeletedAt() != null,
            projection.getMessageText(),
            projection.getCreatedAt()
        );
    }

    private static StoredContactUser toStoredUser(ContactUserReadRepository.ContactUserProjection projection) {
        return new StoredContactUser(
            projection.getUserId(),
            projection.getUsername(),
            projection.getDisplayName(),
            projection.getDeletedAt() != null
        );
    }

    private static StoredFriendship toStoredFriendship(FriendshipEntity entity) {
        return new StoredFriendship(entity.getId(), entity.getUserLowId(), entity.getUserHighId(), entity.getCreatedAt());
    }

    private static StoredFriendshipRequest toStoredFriendshipRequest(FriendshipRequestEntity entity) {
        return new StoredFriendshipRequest(
            entity.getId(),
            entity.getRequesterUserId(),
            entity.getRecipientUserId(),
            entity.getMessageText(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getRespondedAt()
        );
    }

    private static StoredUserBlock toStoredUserBlock(UserBlockEntity entity) {
        return new StoredUserBlock(
            entity.getId(),
            entity.getBlockerUserId(),
            entity.getBlockedUserId(),
            entity.getCreatedAt()
        );
    }

    private static boolean isConstraint(DataIntegrityViolationException exception, String constraintName) {
        String message = Optional.ofNullable(exception.getMostSpecificCause())
            .map(Throwable::getMessage)
            .orElse(exception.getMessage());
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraintName.toLowerCase(Locale.ROOT));
    }
}
