package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserBlockJpaRepository extends JpaRepository<UserBlockEntity, UUID> {

    Optional<UserBlockEntity> findByBlockerUserIdAndBlockedUserId(UUID blockerUserId, UUID blockedUserId);

    long deleteByBlockerUserIdAndBlockedUserId(UUID blockerUserId, UUID blockedUserId);

    long deleteByBlockerUserIdOrBlockedUserId(UUID blockerUserId, UUID blockedUserId);

    @Query(
        value = """
            select
                b.blocked_user_id as otherUserId,
                u.username as otherUsername,
                u.display_name as otherDisplayName,
                u.deleted_at as otherDeletedAt,
                b.created_at as blockedAt
            from user_blocks b
            join users u on u.id = b.blocked_user_id
            where b.blocker_user_id = :userId
            order by b.created_at desc, lower(u.username), b.id
            """,
        nativeQuery = true
    )
    List<BlockedUserProjection> findBlockedContacts(@Param("userId") UUID userId);

    interface BlockedUserProjection {

        UUID getOtherUserId();

        String getOtherUsername();

        String getOtherDisplayName();

        Instant getOtherDeletedAt();

        Instant getBlockedAt();
    }
}
