package edu.artemiy.chat.rooms.api;

public class RoomsException extends RuntimeException {

    private final String code;
    private final RoomsErrorType errorType;

    public RoomsException(String code, String message, RoomsErrorType errorType) {
        super(message);
        this.code = code;
        this.errorType = errorType;
    }

    public String code() {
        return code;
    }

    public RoomsErrorType errorType() {
        return errorType;
    }
}
