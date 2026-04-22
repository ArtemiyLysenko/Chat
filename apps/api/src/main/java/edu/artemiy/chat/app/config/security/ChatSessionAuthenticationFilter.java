package edu.artemiy.chat.app.config.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import edu.artemiy.chat.app.config.ChatProperties;
import edu.artemiy.chat.identity.api.ClientContext;
import edu.artemiy.chat.identity.api.IdentityService;

@Component
public class ChatSessionAuthenticationFilter extends OncePerRequestFilter {

    private final IdentityService identityService;
    private final ChatProperties chatProperties;

    ChatSessionAuthenticationFilter(IdentityService identityService, ChatProperties chatProperties) {
        this.identityService = identityService;
        this.chatProperties = chatProperties;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String sessionId = readSessionCookie(request);
            if (sessionId != null) {
                identityService.authenticateSession(sessionId, clientContext(request)).ifPresent(authenticatedSession -> {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    if (isAdmin(authenticatedSession.username())) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        authenticatedSession,
                        sessionId,
                        authorities
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAdmin(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        return chatProperties.getAuth().getAdminUsernames().stream()
            .filter(candidate -> candidate != null && !candidate.isBlank())
            .map(candidate -> candidate.trim().toLowerCase(Locale.ROOT))
            .anyMatch(normalizedUsername::equals);
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

    public static ClientContext clientContext(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ipAddress = forwardedFor == null || forwardedFor.isBlank()
            ? request.getRemoteAddr()
            : forwardedFor.split(",")[0].trim();
        String userAgent = request.getHeader("User-Agent");
        return new ClientContext(userAgent == null ? "" : userAgent, ipAddress == null ? "" : ipAddress);
    }
}
