package edu.artemiy.chat.identity.spi;

import java.util.Optional;
import java.util.UUID;

public interface UserPersistencePort {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<StoredUser> findByEmail(String email);

    Optional<StoredUser> findById(UUID userId);

    StoredUser create(NewUserRecord newUser);

    void updatePassword(UUID userId, String passwordHash);

    void tombstone(TombstoneUserRecord tombstoneUser);
}
