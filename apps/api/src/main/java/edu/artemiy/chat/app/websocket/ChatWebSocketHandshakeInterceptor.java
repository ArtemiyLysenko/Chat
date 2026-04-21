package edu.artemiy.chat.app.websocket;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.HandshakeInterceptor;

import edu.artemiy.chat.app.config.ChatProperties;
import edu.artemiy.chat.app.config.security.ChatSessionAuthenticationFilter;
import edu.artemiy.chat.identity.api.AuthenticatedSession;
import edu.artemiy.chat.identity.api.IdentityService;

@Component
class ChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    static final String AUTHENTICATED_SESSION_ATTRIBUTE = AuthenticatedSession.class.getName();

    private final IdentityService identityService;
    private final ChatProperties chatProperties;

    ChatWebSocketHandshakeInterceptor(IdentityService identityService, ChatProperties chatProperties) {
        this.identityService = identityService;
        this.chatProperties = chatProperties;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        org.springframework.web.socket.WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
        String sessionId = readSessionCookie(httpServletRequest);
        if (sessionId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        AuthenticatedSession authenticatedSession = identityService.authenticateSession(
            sessionId,
            ChatSessionAuthenticationFilter.clientContext(httpServletRequest)
        ).orElse(null);
        if (authenticatedSession == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(AUTHENTICATED_SESSION_ATTRIBUTE, authenticatedSession);
        return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        org.springframework.web.socket.WebSocketHandler wsHandler,
        Exception exception
    ) {
    }

    private String readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (chatProperties.getAuth().getCookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
