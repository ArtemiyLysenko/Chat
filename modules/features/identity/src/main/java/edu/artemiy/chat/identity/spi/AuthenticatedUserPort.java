package edu.artemiy.chat.identity.spi;

import java.util.Optional;

public interface AuthenticatedUserPort {

    Optional<AuthenticatedUser> currentUser();
}
