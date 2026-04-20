package edu.artemiy.chat.adapters.persistence.jpa.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

    @Query("""
        select token
        from PasswordResetTokenEntity token
        where token.tokenHash = :tokenHash
          and token.usedAt is null
          and token.expiresAt > :now
        """)
    Optional<PasswordResetTokenEntity> findUsableByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PasswordResetTokenEntity token
        set token.usedAt = :usedAt
        where token.id = :tokenId
          and token.usedAt is null
        """)
    int markUsed(@Param("tokenId") UUID tokenId, @Param("usedAt") Instant usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PasswordResetTokenEntity token
        set token.usedAt = :invalidatedAt
        where token.userId = :userId
          and token.usedAt is null
          and token.expiresAt > :invalidatedAt
        """)
    int invalidateOutstandingTokens(@Param("userId") UUID userId, @Param("invalidatedAt") Instant invalidatedAt);
}
