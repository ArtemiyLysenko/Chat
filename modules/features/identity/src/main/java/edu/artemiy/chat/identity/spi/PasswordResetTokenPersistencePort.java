package edu.artemiy.chat.identity.spi;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenPersistencePort {

    StoredPasswordResetToken create(NewPasswordResetTokenRecord newToken);

    Optional<StoredPasswordResetToken> findUsableByTokenHash(String tokenHash, Instant now);

    boolean markUsed(UUID tokenId, Instant usedAt);

    void invalidateOutstandingTokens(UUID userId, Instant invalidatedAt);
}
