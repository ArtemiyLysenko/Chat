package edu.artemiy.chat.messaging.api;

public class MessagingException extends RuntimeException {

    private final String code;
    private final MessagingErrorType errorType;

    public MessagingException(String code, String message, MessagingErrorType errorType) {
        super(message);
        this.code = code;
        this.errorType = errorType;
    }

    public String code() {
        return code;
    }

    public MessagingErrorType errorType() {
        return errorType;
    }
}
