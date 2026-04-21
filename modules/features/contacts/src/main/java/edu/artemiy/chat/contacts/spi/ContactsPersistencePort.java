package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactsPersistencePort {

    Optional<StoredContactUser> findUserById(UUID userId);

    Optional<StoredContactUser> findActiveUserById(UUID userId);

    Optional<StoredContactUser> findActiveUserByUsername(String username);

    void lockUserPair(UUID firstUserId, UUID secondUserId);

    Optional<StoredFriendship> findFriendship(UUID userLowId, UUID userHighId);

    Optional<StoredFriendshipRequest> findPendingFriendRequest(UUID requesterUserId, UUID recipientUserId);

    Optional<StoredFriendshipRequest> findFriendshipRequest(UUID requestId);

    Optional<StoredFriendshipRequest> findPendingFriendRequestForRecipient(UUID requestId, UUID recipientUserId);

    Optional<StoredUserBlock> findUserBlock(UUID blockerUserId, UUID blockedUserId);

    StoredFriendshipRequest createFriendshipRequest(NewFriendshipRequestRecord request);

    StoredFriendship createFriendship(NewFriendshipRecord friendship);

    StoredUserBlock createUserBlock(NewUserBlockRecord block);

    void markFriendshipRequestAccepted(UUID requestId, Instant respondedAt);

    void markFriendshipRequestRejected(UUID requestId, Instant respondedAt);

    void rejectPendingFriendRequestsBetween(UUID firstUserId, UUID secondUserId, Instant respondedAt);

    void deleteFriendship(UUID friendshipId);

    void deleteUserBlock(UUID blockerUserId, UUID blockedUserId);

    List<StoredFriendContactEntry> listFriends(UUID userId);

    List<StoredPendingFriendRequestEntry> listInboundPendingRequests(UUID userId);

    List<StoredPendingFriendRequestEntry> listOutboundPendingRequests(UUID userId);

    List<StoredBlockedContactEntry> listBlockedUsers(UUID userId);
}
