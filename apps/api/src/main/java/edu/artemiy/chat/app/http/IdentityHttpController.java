package edu.artemiy.chat.app.http;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import edu.artemiy.chat.app.config.security.ChatSessionAuthenticationFilter;
import edu.artemiy.chat.identity.api.ChangePasswordCommand;
import edu.artemiy.chat.identity.api.ConsumePasswordResetCommand;
import edu.artemiy.chat.identity.api.DeleteAccountCommand;
import edu.artemiy.chat.identity.api.IdentityService;
import edu.artemiy.chat.identity.api.LoginCommand;
import edu.artemiy.chat.identity.api.LoginSession;
import edu.artemiy.chat.identity.api.RegisterUserCommand;
import edu.artemiy.chat.identity.api.RegisteredUser;
import edu.artemiy.chat.identity.api.RequestPasswordResetCommand;

@RestController
@Validated
class IdentityHttpController {

    private final IdentityService identityService;
    private final SessionCookieSupport sessionCookieSupport;

    IdentityHttpController(IdentityService identityService, SessionCookieSupport sessionCookieSupport) {
        this.identityService = identityService;
        this.sessionCookieSupport = sessionCookieSupport;
    }

    @PostMapping("/api/auth/register")
    ResponseEntity<RegisteredUser> register(@Valid @RequestBody RegisterRequest request) {
        RegisteredUser registeredUser = identityService.register(new RegisterUserCommand(
            request.email(),
            request.username(),
            request.password()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser);
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<LoginSession> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        LoginSession loginSession = identityService.login(new LoginCommand(
            request.email(),
            request.password(),
            ChatSessionAuthenticationFilter.clientContext(httpServletRequest)
        ));
        HttpHeaders headers = new HttpHeaders();
        sessionCookieSupport.setLoginCookie(headers, loginSession);
        return new ResponseEntity<>(loginSession, headers, HttpStatus.OK);
    }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout() {
        identityService.logout();
        HttpHeaders headers = new HttpHeaders();
        sessionCookieSupport.clearCookie(headers);
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }

    @PostMapping("/api/auth/password/change")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        identityService.changePassword(new ChangePasswordCommand(request.currentPassword(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/password/reset-requests")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        URI baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUri();
        identityService.requestPasswordReset(new RequestPasswordResetCommand(
            request.email(),
            baseUri.toString()
        ));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/auth/password/reset")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConsumeRequest request) {
        identityService.resetPassword(new ConsumePasswordResetCommand(request.token(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/account")
    ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        identityService.deleteAccount(new DeleteAccountCommand(request.currentPassword()));
        HttpHeaders headers = new HttpHeaders();
        sessionCookieSupport.clearCookie(headers);
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }

    private record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,32}$") String username,
        @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    private record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {
    }

    private record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
    }

    private record PasswordResetRequest(@NotBlank @Email String email) {
    }

    private record PasswordResetConsumeRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
    }

    private record DeleteAccountRequest(@NotBlank String currentPassword) {
    }
}
