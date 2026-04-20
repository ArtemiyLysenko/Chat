package edu.artemiy.chat.adapters.persistence.jpa.identity;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.identity.api.AuthenticatedSession;
import edu.artemiy.chat.identity.spi.AuthenticatedUser;
import edu.artemiy.chat.identity.spi.AuthenticatedUserPort;

@Component
class SecurityContextAuthenticatedUserAdapter implements AuthenticatedUserPort {

    @Override
    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof AuthenticatedSession authenticatedSession)) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(authenticatedSession.userId(), authenticatedSession.sessionId()));
    }
}
