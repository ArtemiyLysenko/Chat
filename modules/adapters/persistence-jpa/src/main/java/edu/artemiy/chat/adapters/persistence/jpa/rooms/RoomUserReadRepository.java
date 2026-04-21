package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface RoomUserReadRepository extends Repository<RoomEntity, UUID> {

    @Query(
        value = """
            select
                id as userId,
                username as username,
                display_name as displayName,
                deleted_at as deletedAt
            from users
            where id in (:userIds)
            """,
        nativeQuery = true
    )
    List<RoomUserProjection> findUsers(@Param("userIds") List<UUID> userIds);

    interface RoomUserProjection {

        UUID getUserId();

        String getUsername();

        String getDisplayName();

        Instant getDeletedAt();
    }
}
