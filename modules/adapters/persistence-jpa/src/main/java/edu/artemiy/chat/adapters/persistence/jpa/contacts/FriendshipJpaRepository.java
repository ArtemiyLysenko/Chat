package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FriendshipJpaRepository extends JpaRepository<FriendshipEntity, UUID> {

    Optional<FriendshipEntity> findByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);

    long deleteByUserLowIdOrUserHighId(UUID userLowId, UUID userHighId);

    @Query(
        value = """
            select
                f.id as friendshipId,
                case
                    when f.user_low_id = :userId then f.user_high_id
                    else f.user_low_id
                end as otherUserId,
                u.username as otherUsername,
                u.display_name as otherDisplayName,
                u.deleted_at as otherDeletedAt,
                f.created_at as friendsSince
            from friendships f
            join users u
                on u.id = case
                    when f.user_low_id = :userId then f.user_high_id
                    else f.user_low_id
                end
            where f.user_low_id = :userId
               or f.user_high_id = :userId
            order by lower(u.username), f.created_at, f.id
            """,
        nativeQuery = true
    )
    List<FriendshipProjection> findFriendContacts(@Param("userId") UUID userId);

    interface FriendshipProjection {

        UUID getFriendshipId();

        UUID getOtherUserId();

        String getOtherUsername();

        String getOtherDisplayName();

        Instant getOtherDeletedAt();

        Instant getFriendsSince();
    }
}
