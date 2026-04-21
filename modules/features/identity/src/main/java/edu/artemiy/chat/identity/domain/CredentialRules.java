package edu.artemiy.chat.identity.domain;

import java.util.Locale;

import edu.artemiy.chat.core.kernel.UsernameRules;
import edu.artemiy.chat.identity.api.IdentityErrorType;
import edu.artemiy.chat.identity.api.IdentityException;

public final class CredentialRules {

    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 72;

    private CredentialRules() {
    }

    public static String normalizeEmail(String email) {
        String normalized = requireText(email, "email", "Email is required.")
            .toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new IdentityException("identity.invalid_email", "Email must be a valid address.", IdentityErrorType.BAD_REQUEST);
        }
        return normalized;
    }

    public static String normalizeUsername(String username) {
        try {
            return UsernameRules.normalize(username);
        }
        catch (UsernameRules.InvalidUsernameException exception) {
            throw switch (exception.reason()) {
                case MISSING -> new IdentityException(
                    "identity.missing_username",
                    exception.getMessage(),
                    IdentityErrorType.BAD_REQUEST
                );
                case INVALID -> new IdentityException(
                    "identity.invalid_username",
                    exception.getMessage(),
                    IdentityErrorType.BAD_REQUEST
                );
            };
        }
    }

    public static void validatePassword(String password) {
        String trimmed = requireText(password, "password", "Password is required.");
        if (trimmed.length() < PASSWORD_MIN_LENGTH || trimmed.length() > PASSWORD_MAX_LENGTH) {
            throw new IdentityException(
                "identity.invalid_password",
                "Password must be between 8 and 72 characters.",
                IdentityErrorType.BAD_REQUEST
            );
        }
    }

    private static String requireText(String value, String field, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IdentityException("identity.missing_" + field, message, IdentityErrorType.BAD_REQUEST);
        }
        return trimmed;
    }
}
