package edu.artemiy.chat.app.http;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.app.config.ChatProperties;
import edu.artemiy.chat.identity.api.LoginSession;

@Component
class SessionCookieSupport {

    private final ChatProperties chatProperties;

    SessionCookieSupport(ChatProperties chatProperties) {
        this.chatProperties = chatProperties;
    }

    String loginCookieHeader(LoginSession loginSession) {
        return ResponseCookie.from(chatProperties.getAuth().getCookieName(), loginSession.sessionId().toString())
            .httpOnly(true)
            .path("/")
            .sameSite("Lax")
            .secure(chatProperties.getAuth().isSecureCookie())
            .maxAge(chatProperties.getAuth().getSessionTtl())
            .build()
            .toString();
    }

    String clearCookieHeader() {
        return ResponseCookie.from(chatProperties.getAuth().getCookieName(), "")
            .httpOnly(true)
            .path("/")
            .sameSite("Lax")
            .secure(chatProperties.getAuth().isSecureCookie())
            .maxAge(Duration.ZERO)
            .build()
            .toString();
    }

    void setLoginCookie(HttpHeaders headers, LoginSession loginSession) {
        headers.add(HttpHeaders.SET_COOKIE, loginCookieHeader(loginSession));
    }

    void clearCookie(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, clearCookieHeader());
    }
}
