package edu.artemiy.chat.app.http;

import java.util.UUID;

import org.springframework.security.core.Authentication;

import edu.artemiy.chat.identity.api.AuthenticatedSession;

final class AuthenticatedHttpUserSupport {

    private AuthenticatedHttpUserSupport() {
    }

    static UUID userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedSession authenticatedSession) {
            return authenticatedSession.userId();
        }
        throw new IllegalStateException("Authenticated session principal is required.");
    }
}
