package edu.artemiy.chat.identity.api;

import java.util.List;
import java.util.Optional;

public interface IdentityService {

    RegisteredUser register(RegisterUserCommand command);

    LoginSession login(LoginCommand command);

    void logout();

    List<SessionSummary> listSessions();

    SessionRevocationResult revokeSession(String sessionId);

    void changePassword(ChangePasswordCommand command);

    void requestPasswordReset(RequestPasswordResetCommand command);

    void resetPassword(ConsumePasswordResetCommand command);

    void deleteAccount(DeleteAccountCommand command);

    Optional<AuthenticatedSession> authenticateSession(String sessionId, ClientContext clientContext);
}
