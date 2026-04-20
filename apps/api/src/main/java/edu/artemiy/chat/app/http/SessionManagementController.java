package edu.artemiy.chat.app.http;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import edu.artemiy.chat.identity.api.IdentityService;
import edu.artemiy.chat.identity.api.SessionSummary;

@RestController
class SessionManagementController {

    private final IdentityService identityService;
    private final SessionCookieSupport sessionCookieSupport;

    SessionManagementController(IdentityService identityService, SessionCookieSupport sessionCookieSupport) {
        this.identityService = identityService;
        this.sessionCookieSupport = sessionCookieSupport;
    }

    @GetMapping("/api/sessions")
    List<SessionSummary> listSessions() {
        return identityService.listSessions();
    }

    @DeleteMapping("/api/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        var result = identityService.revokeSession(sessionId);
        if (!result.currentSessionRevoked()) {
            return ResponseEntity.noContent().build();
        }

        HttpHeaders headers = new HttpHeaders();
        sessionCookieSupport.clearCookie(headers);
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }
}
