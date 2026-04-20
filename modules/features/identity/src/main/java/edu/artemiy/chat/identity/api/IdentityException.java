package edu.artemiy.chat.identity.api;

public final class IdentityException extends RuntimeException {

    private final String code;
    private final IdentityErrorType errorType;

    public IdentityException(String code, String message, IdentityErrorType errorType) {
        super(message);
        this.code = code;
        this.errorType = errorType;
    }

    public String code() {
        return code;
    }

    public IdentityErrorType errorType() {
        return errorType;
    }
}
