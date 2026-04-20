package edu.artemiy.chat.identity.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import edu.artemiy.chat.identity.api.IdentityErrorType;
import edu.artemiy.chat.identity.api.IdentityException;

public final class CredentialRules {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,32}$");
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
        String normalized = Normalizer.normalize(requireText(username, "username", "Username is required."), Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IdentityException(
                "identity.invalid_username",
                "Username must use 3 to 32 lowercase letters, digits, dots, dashes, or underscores.",
                IdentityErrorType.BAD_REQUEST
            );
        }
        return normalized;
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
