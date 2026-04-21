package edu.artemiy.chat.app.config.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import edu.artemiy.chat.app.config.ChatProperties;

@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ChatSessionAuthenticationFilter chatSessionAuthenticationFilter,
        ChatProperties chatProperties
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-CSRF-TOKEN");
        csrfTokenRepository.setCookieCustomizer(builder -> builder
            .path("/")
            .sameSite("Lax")
            .secure(chatProperties.getAuth().isSecureCookie()));

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
            )
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/",
                    "/login",
                    "/register",
                    "/password-reset/request",
                    "/password-reset/consume",
                    "/index.html",
                    "/login.html",
                    "/register.html",
                    "/password-reset-request.html",
                    "/password-reset-consume.html",
                    "/assets/**",
                    "/js/**",
                    "/favicon.ico"
                ).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/app", "/app/sessions", "/app.html", "/sessions.html").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/bootstrap").permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/password/reset-requests",
                    "/api/auth/password/reset"
                ).permitAll()
                .requestMatchers("/api/admin/**").denyAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> commenceAuthentication(request.getRequestURI(), response))
            )
            .addFilterBefore(chatSessionAuthenticationFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }

    private static void commenceAuthentication(String requestUri, HttpServletResponse response) throws IOException {
        if (requestUri.startsWith("/api/")) {
            writeUnauthorized(response);
            return;
        }
        response.sendRedirect("/login");
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"identity.unauthenticated\",\"message\":\"Authentication is required.\"}");
    }
}
