package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactsPersistencePort {

    Optional<StoredContactUser> findActiveUserById(UUID userId);

    Optional<StoredContactUser> findActiveUserByUsername(String username);

    void lockUserPair(UUID firstUserId, UUID secondUserId);

    Optional<StoredFriendship> findFriendship(UUID userLowId, UUID userHighId);

    Optional<StoredFriendshipRequest> findPendingFriendRequest(UUID requesterUserId, UUID recipientUserId);

    Optional<StoredFriendshipRequest> findFriendshipRequest(UUID requestId);

    Optional<StoredFriendshipRequest> findPendingFriendRequestForRecipient(UUID requestId, UUID recipientUserId);

    StoredFriendshipRequest createFriendshipRequest(NewFriendshipRequestRecord request);

    StoredFriendship createFriendship(NewFriendshipRecord friendship);

    void markFriendshipRequestAccepted(UUID requestId, Instant respondedAt);

    void markFriendshipRequestRejected(UUID requestId, Instant respondedAt);

    List<StoredFriendContactEntry> listFriends(UUID userId);

    List<StoredPendingFriendRequestEntry> listInboundPendingRequests(UUID userId);

    List<StoredPendingFriendRequestEntry> listOutboundPendingRequests(UUID userId);
}
