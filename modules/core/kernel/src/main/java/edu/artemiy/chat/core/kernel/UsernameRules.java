package edu.artemiy.chat.core.kernel;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UsernameRules {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,32}$");

    private UsernameRules() {
    }

    public static String normalize(String username) {
        String trimmed = username == null ? "" : username.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidUsernameException(InvalidUsernameReason.MISSING);
        }

        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new InvalidUsernameException(InvalidUsernameReason.INVALID);
        }
        return normalized;
    }

    public enum InvalidUsernameReason {
        MISSING("Username is required."),
        INVALID("Username must use 3 to 32 lowercase letters, digits, dots, dashes, or underscores.");

        private final String message;

        InvalidUsernameReason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    public static final class InvalidUsernameException extends IllegalArgumentException {

        private final InvalidUsernameReason reason;

        public InvalidUsernameException(InvalidUsernameReason reason) {
            super(reason.message());
            this.reason = reason;
        }

        public InvalidUsernameReason reason() {
            return reason;
        }
    }
}
