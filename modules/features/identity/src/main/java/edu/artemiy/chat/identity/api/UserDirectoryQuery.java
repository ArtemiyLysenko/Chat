package edu.artemiy.chat.identity.api;

import java.util.Optional;

public interface UserDirectoryQuery {

    ResolvedUser authenticateByUsernamePassword(UsernamePasswordAuthenticationCommand command);

    Optional<ResolvedUser> findActiveUserByUsername(String username);
}
