package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ContactUserReadRepository extends Repository<FriendshipRequestEntity, UUID> {

    @Query(
        value = """
            select
                id as userId,
                username as username,
                display_name as displayName,
                deleted_at as deletedAt
            from users
            where id = :userId
              and deleted_at is null
            """,
        nativeQuery = true
    )
    Optional<ContactUserProjection> findActiveUserById(@Param("userId") UUID userId);

    @Query(
        value = """
            select
                id as userId,
                username as username,
                display_name as displayName,
                deleted_at as deletedAt
            from users
            where lower(username) = lower(:username)
              and deleted_at is null
            """,
        nativeQuery = true
    )
    Optional<ContactUserProjection> findActiveUserByUsername(@Param("username") String username);

    interface ContactUserProjection {

        UUID getUserId();

        String getUsername();

        String getDisplayName();

        Instant getDeletedAt();
    }
}
