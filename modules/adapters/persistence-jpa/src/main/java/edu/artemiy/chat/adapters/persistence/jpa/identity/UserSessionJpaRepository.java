package edu.artemiy.chat.adapters.persistence.jpa.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserSessionJpaRepository extends JpaRepository<UserSessionEntity, UUID> {

    @Query("""
        select session
        from UserSessionEntity session
        where session.id = :sessionId
          and session.revokedAt is null
          and session.expiresAt > :now
        """)
    Optional<UserSessionEntity> findActiveById(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Query("""
        select session
        from UserSessionEntity session
        where session.userId = :userId
          and session.revokedAt is null
          and session.expiresAt > :now
        order by session.lastSeenAt desc, session.createdAt desc
        """)
    List<UserSessionEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
        select session.id
        from UserSessionEntity session
        where session.userId = :userId
          and session.revokedAt is null
          and session.expiresAt > :now
        """)
    List<UUID> findActiveIdsByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
        select session.id
        from UserSessionEntity session
        where session.userId = :userId
          and session.id <> :currentSessionId
          and session.revokedAt is null
          and session.expiresAt > :now
        """)
    List<UUID> findActiveIdsByUserIdExcluding(
        @Param("userId") UUID userId,
        @Param("currentSessionId") UUID currentSessionId,
        @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update UserSessionEntity session
        set session.lastSeenAt = :lastSeenAt,
            session.userAgent = :userAgent,
            session.ipAddress = :ipAddress
        where session.id = :sessionId
          and session.revokedAt is null
          and session.expiresAt > :lastSeenAt
        """)
    int touch(
        @Param("sessionId") UUID sessionId,
        @Param("lastSeenAt") Instant lastSeenAt,
        @Param("userAgent") String userAgent,
        @Param("ipAddress") String ipAddress
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update UserSessionEntity session
        set session.revokedAt = :revokedAt
        where session.userId = :userId
          and session.id = :sessionId
          and session.revokedAt is null
          and session.expiresAt > :revokedAt
        """)
    int revokeSession(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update UserSessionEntity session
        set session.revokedAt = :revokedAt
        where session.userId = :userId
          and session.revokedAt is null
          and session.expiresAt > :revokedAt
        """)
    int revokeAllSessions(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update UserSessionEntity session
        set session.revokedAt = :revokedAt
        where session.userId = :userId
          and session.id <> :currentSessionId
          and session.revokedAt is null
          and session.expiresAt > :revokedAt
        """)
    int revokeAllOtherSessions(
        @Param("userId") UUID userId,
        @Param("currentSessionId") UUID currentSessionId,
        @Param("revokedAt") Instant revokedAt
    );
}
