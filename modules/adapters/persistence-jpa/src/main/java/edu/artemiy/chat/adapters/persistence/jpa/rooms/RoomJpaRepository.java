package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoomJpaRepository extends JpaRepository<RoomEntity, UUID> {

    List<RoomEntity> findAllByOwnerUserId(UUID ownerUserId);

    @Query(
        value = """
            select
                r.id as roomId,
                r.name as name,
                r.description as description,
                r.visibility as visibility,
                r.owner_user_id as ownerUserId,
                m.role as viewerRole,
                count(all_members.user_id) as memberCount
            from room_memberships m
            join rooms r on r.id = m.room_id
            left join room_memberships all_members on all_members.room_id = r.id
            where m.user_id = :userId
            group by r.id, r.name, r.description, r.visibility, r.owner_user_id, m.role, r.created_at
            order by lower(r.name), r.created_at
            """,
        nativeQuery = true
    )
    List<RoomListProjection> listJoinedRooms(@Param("userId") UUID userId);

    @Query(
        value = """
            select
                r.id as roomId,
                r.name as name,
                r.description as description,
                r.visibility as visibility,
                r.owner_user_id as ownerUserId,
                viewer.role as viewerRole,
                count(all_members.user_id) as memberCount
            from rooms r
            left join room_memberships viewer
                on viewer.room_id = r.id and viewer.user_id = :userId
            left join room_memberships all_members
                on all_members.room_id = r.id
            left join room_bans bans
                on bans.room_id = r.id and bans.user_id = :userId
            where r.visibility = 'PUBLIC'
              and bans.user_id is null
            group by r.id, r.name, r.description, r.visibility, r.owner_user_id, viewer.role, r.created_at
            order by lower(r.name), r.created_at
            """,
        nativeQuery = true
    )
    List<RoomListProjection> listCatalogRooms(@Param("userId") UUID userId);

    interface RoomListProjection {

        UUID getRoomId();

        String getName();

        String getDescription();

        String getVisibility();

        UUID getOwnerUserId();

        String getViewerRole();

        long getMemberCount();
    }
}
