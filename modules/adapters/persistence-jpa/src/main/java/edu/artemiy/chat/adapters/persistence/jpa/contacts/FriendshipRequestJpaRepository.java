package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.artemiy.chat.contacts.spi.FriendshipRequestStatus;

interface FriendshipRequestJpaRepository extends JpaRepository<FriendshipRequestEntity, UUID> {

    long deleteByRequesterUserIdOrRecipientUserId(UUID requesterUserId, UUID recipientUserId);

    Optional<FriendshipRequestEntity> findByRequesterUserIdAndRecipientUserIdAndStatus(
        UUID requesterUserId,
        UUID recipientUserId,
        FriendshipRequestStatus status
    );

    Optional<FriendshipRequestEntity> findByIdAndRecipientUserIdAndStatus(
        UUID id,
        UUID recipientUserId,
        FriendshipRequestStatus status
    );

    @Query(
        value = """
            select
                r.id as requestId,
                r.requester_user_id as otherUserId,
                u.username as otherUsername,
                u.display_name as otherDisplayName,
                u.deleted_at as otherDeletedAt,
                r.message_text as messageText,
                r.created_at as createdAt
            from friendship_requests r
            join users u on u.id = r.requester_user_id
            where r.recipient_user_id = :userId
              and r.status = 'PENDING'
            order by r.created_at desc, r.id desc
            """,
        nativeQuery = true
    )
    List<PendingFriendRequestProjection> findInboundPendingRequests(@Param("userId") UUID userId);

    @Query(
        value = """
            select
                r.id as requestId,
                r.recipient_user_id as otherUserId,
                u.username as otherUsername,
                u.display_name as otherDisplayName,
                u.deleted_at as otherDeletedAt,
                r.message_text as messageText,
                r.created_at as createdAt
            from friendship_requests r
            join users u on u.id = r.recipient_user_id
            where r.requester_user_id = :userId
              and r.status = 'PENDING'
            order by r.created_at desc, r.id desc
            """,
        nativeQuery = true
    )
    List<PendingFriendRequestProjection> findOutboundPendingRequests(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
            update FriendshipRequestEntity r
            set r.status = edu.artemiy.chat.contacts.spi.FriendshipRequestStatus.REJECTED,
                r.respondedAt = :respondedAt
            where r.status = edu.artemiy.chat.contacts.spi.FriendshipRequestStatus.PENDING
              and (
                    (r.requesterUserId = :firstUserId and r.recipientUserId = :secondUserId)
                 or (r.requesterUserId = :secondUserId and r.recipientUserId = :firstUserId)
              )
            """
    )
    int rejectPendingRequestsBetween(
        @Param("firstUserId") UUID firstUserId,
        @Param("secondUserId") UUID secondUserId,
        @Param("respondedAt") Instant respondedAt
    );

    interface PendingFriendRequestProjection {

        UUID getRequestId();

        UUID getOtherUserId();

        String getOtherUsername();

        String getOtherDisplayName();

        Instant getOtherDeletedAt();

        String getMessageText();

        Instant getCreatedAt();
    }
}
