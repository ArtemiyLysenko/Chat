package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoomMembershipJpaRepository extends JpaRepository<RoomMembershipEntity, RoomMembershipEntity.RoomMembershipId> {

    Optional<RoomMembershipEntity> findById(RoomMembershipEntity.RoomMembershipId id);

    void deleteByIdUserId(UUID userId);

    @Query(
        value = """
            select
                user_id as userId,
                role as role,
                joined_at as joinedAt
            from room_memberships
            where room_id = :roomId
            order by
                case role when 'OWNER' then 0 when 'ADMIN' then 1 else 2 end,
                joined_at,
                user_id
            """,
        nativeQuery = true
    )
    List<RoomMemberProjection> findMembers(@Param("roomId") UUID roomId);

    interface RoomMemberProjection {

        UUID getUserId();

        String getRole();

        Instant getJoinedAt();
    }
}
