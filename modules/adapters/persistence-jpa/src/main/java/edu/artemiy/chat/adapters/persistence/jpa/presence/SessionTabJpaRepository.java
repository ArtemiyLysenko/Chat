package edu.artemiy.chat.adapters.persistence.jpa.presence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SessionTabJpaRepository extends JpaRepository<SessionTabEntity, UUID> {

    Optional<SessionTabEntity> findBySessionIdAndTabKey(UUID sessionId, String tabKey);

    @Query(
        value = """
            select
                tab.id as id,
                session.user_id as userId,
                tab.session_id as sessionId,
                tab.tab_key as tabKey,
                tab.connected_at as connectedAt,
                tab.last_activity_at as lastActivityAt,
                tab.last_ping_at as lastPingAt,
                tab.closed_at as closedAt
            from session_tabs tab
            join user_sessions session on session.id = tab.session_id
            where session.user_id in (:userIds)
              and session.revoked_at is null
              and session.expires_at > :now
              and tab.closed_at is null
            """,
        nativeQuery = true
    )
    List<SessionTabPresenceProjection> findOpenTabsByUserIds(@Param("userIds") Collection<UUID> userIds, @Param("now") java.time.Instant now);

    interface SessionTabPresenceProjection {

        UUID getId();

        UUID getUserId();

        UUID getSessionId();

        String getTabKey();

        java.time.Instant getConnectedAt();

        java.time.Instant getLastActivityAt();

        java.time.Instant getLastPingAt();

        java.time.Instant getClosedAt();
    }
}
