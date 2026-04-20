package edu.artemiy.chat.adapters.persistence.jpa.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.identity.spi.NewPasswordResetTokenRecord;
import edu.artemiy.chat.identity.spi.PasswordResetTokenPersistencePort;
import edu.artemiy.chat.identity.spi.StoredPasswordResetToken;

@Component
class JpaPasswordResetTokenPersistenceAdapter implements PasswordResetTokenPersistencePort {

    private final PasswordResetTokenJpaRepository passwordResetTokenJpaRepository;

    JpaPasswordResetTokenPersistenceAdapter(PasswordResetTokenJpaRepository passwordResetTokenJpaRepository) {
        this.passwordResetTokenJpaRepository = passwordResetTokenJpaRepository;
    }

    @Override
    public StoredPasswordResetToken create(NewPasswordResetTokenRecord newToken) {
        PasswordResetTokenEntity saved = passwordResetTokenJpaRepository.save(new PasswordResetTokenEntity(
            newToken.id(),
            newToken.userId(),
            newToken.tokenHash(),
            newToken.createdAt(),
            newToken.expiresAt(),
            null
        ));
        return toStoredToken(saved);
    }

    @Override
    public Optional<StoredPasswordResetToken> findUsableByTokenHash(String tokenHash, Instant now) {
        return passwordResetTokenJpaRepository.findUsableByTokenHash(tokenHash, now).map(JpaPasswordResetTokenPersistenceAdapter::toStoredToken);
    }

    @Override
    @Transactional
    public boolean markUsed(UUID tokenId, Instant usedAt) {
        return passwordResetTokenJpaRepository.markUsed(tokenId, usedAt) > 0;
    }

    @Override
    @Transactional
    public void invalidateOutstandingTokens(UUID userId, Instant invalidatedAt) {
        passwordResetTokenJpaRepository.invalidateOutstandingTokens(userId, invalidatedAt);
    }

    private static StoredPasswordResetToken toStoredToken(PasswordResetTokenEntity entity) {
        return new StoredPasswordResetToken(
            entity.getId(),
            entity.getUserId(),
            entity.getTokenHash(),
            entity.getCreatedAt(),
            entity.getExpiresAt(),
            entity.getUsedAt()
        );
    }
}
