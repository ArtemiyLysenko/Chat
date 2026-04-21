package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoomBanJpaRepository extends JpaRepository<RoomBanEntity, RoomBanEntity.RoomBanId> {

    Optional<RoomBanEntity> findById(RoomBanEntity.RoomBanId id);

    void deleteByIdUserId(UUID userId);

    @Query(
        value = """
            select
                user_id as userId,
                banned_by_user_id as bannedByUserId,
                reason as reason,
                created_at as createdAt
            from room_bans
            where room_id = :roomId
            order by created_at desc, user_id
            """,
        nativeQuery = true
    )
    List<RoomBanProjection> findBanEntries(@Param("roomId") UUID roomId);

    interface RoomBanProjection {

        UUID getUserId();

        UUID getBannedByUserId();

        String getReason();

        Instant getCreatedAt();
    }
}
