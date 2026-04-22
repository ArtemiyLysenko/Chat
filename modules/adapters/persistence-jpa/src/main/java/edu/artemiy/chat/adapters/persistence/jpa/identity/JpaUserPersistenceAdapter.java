package edu.artemiy.chat.adapters.persistence.jpa.identity;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;

import edu.artemiy.chat.identity.spi.DuplicateUserIdentityException;
import edu.artemiy.chat.identity.spi.NewUserRecord;
import edu.artemiy.chat.identity.spi.StoredUser;
import edu.artemiy.chat.identity.spi.TombstoneUserRecord;
import edu.artemiy.chat.identity.spi.UserPersistencePort;

@Component
class JpaUserPersistenceAdapter implements UserPersistencePort {

    private final UserJpaRepository userJpaRepository;

    JpaUserPersistenceAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsernameIgnoreCase(username);
    }

    @Override
    public Optional<StoredUser> findByEmail(String email) {
        return userJpaRepository.findByEmailIgnoreCase(email).map(JpaUserPersistenceAdapter::toStoredUser);
    }

    @Override
    public Optional<StoredUser> findByUsername(String username) {
        return userJpaRepository.findByUsernameIgnoreCase(username).map(JpaUserPersistenceAdapter::toStoredUser);
    }

    @Override
    public Optional<StoredUser> findById(UUID userId) {
        return userJpaRepository.findById(userId).map(JpaUserPersistenceAdapter::toStoredUser);
    }

    @Override
    public StoredUser create(NewUserRecord newUser) {
        try {
            UserEntity saved = userJpaRepository.saveAndFlush(new UserEntity(
                newUser.id(),
                newUser.email(),
                newUser.username(),
                newUser.displayName(),
                newUser.passwordHash(),
                null,
                newUser.createdAt()
            ));
            return toStoredUser(saved);
        }
        catch (DataIntegrityViolationException exception) {
            throw new DuplicateUserIdentityException(conflictTarget(exception), exception);
        }
    }

    @Override
    public void updatePassword(UUID userId, String passwordHash) {
        UserEntity userEntity = userJpaRepository.findById(userId).orElseThrow();
        userEntity.setPasswordHash(passwordHash);
        userJpaRepository.save(userEntity);
    }

    @Override
    public void tombstone(TombstoneUserRecord tombstoneUser) {
        UserEntity userEntity = userJpaRepository.findById(tombstoneUser.userId()).orElseThrow();
        userEntity.applyTombstone(
            tombstoneUser.email(),
            tombstoneUser.username(),
            tombstoneUser.displayName(),
            tombstoneUser.deletedAt()
        );
        userJpaRepository.save(userEntity);
    }

    private static StoredUser toStoredUser(UserEntity entity) {
        return new StoredUser(
            entity.getId(),
            entity.getEmail(),
            entity.getUsername(),
            entity.getDisplayName(),
            entity.getPasswordHash(),
            entity.getCreatedAt(),
            entity.getDeletedAt()
        );
    }

    private static DuplicateUserIdentityException.ConflictTarget conflictTarget(DataIntegrityViolationException exception) {
        String message = Optional.ofNullable(exception.getMostSpecificCause())
            .map(Throwable::getMessage)
            .orElse(exception.getMessage());
        if (message == null) {
            return DuplicateUserIdentityException.ConflictTarget.UNKNOWN;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("uq_users_email_lower")) {
            return DuplicateUserIdentityException.ConflictTarget.EMAIL;
        }
        if (normalized.contains("uq_users_username_lower")) {
            return DuplicateUserIdentityException.ConflictTarget.USERNAME;
        }
        return DuplicateUserIdentityException.ConflictTarget.UNKNOWN;
    }
}
