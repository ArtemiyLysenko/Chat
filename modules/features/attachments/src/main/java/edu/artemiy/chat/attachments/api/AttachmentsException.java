package edu.artemiy.chat.attachments.api;

import java.util.Objects;

public class AttachmentsException extends RuntimeException {

    private final String code;
    private final AttachmentsErrorType errorType;

    public AttachmentsException(String code, String message, AttachmentsErrorType errorType) {
        super(message);
        this.code = Objects.requireNonNull(code, "Error code is required.");
        this.errorType = Objects.requireNonNull(errorType, "Error type is required.");
    }

    public String code() {
        return code;
    }

    public AttachmentsErrorType errorType() {
        return errorType;
    }
}
