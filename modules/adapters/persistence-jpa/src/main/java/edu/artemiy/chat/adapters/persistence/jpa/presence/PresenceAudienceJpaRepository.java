package edu.artemiy.chat.adapters.persistence.jpa.presence;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface PresenceAudienceJpaRepository extends Repository<SessionTabEntity, UUID> {

    @Query(
        value = """
            select distinct audience_user_id
            from (
                select
                    case
                        when f.user_low_id = :subjectUserId then f.user_high_id
                        else f.user_low_id
                    end as audience_user_id
                from friendships f
                where f.user_low_id = :subjectUserId
                   or f.user_high_id = :subjectUserId

                union

                select m2.user_id as audience_user_id
                from room_memberships m1
                join room_memberships m2 on m2.room_id = m1.room_id
                where m1.user_id = :subjectUserId
            ) audience
            """,
        nativeQuery = true
    )
    Set<UUID> findAudienceUserIds(@Param("subjectUserId") UUID subjectUserId);
}
