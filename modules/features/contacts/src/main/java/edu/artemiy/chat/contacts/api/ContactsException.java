package edu.artemiy.chat.contacts.api;

public class ContactsException extends RuntimeException {

    private final String code;
    private final ContactsErrorType errorType;

    public ContactsException(String code, String message, ContactsErrorType errorType) {
        super(message);
        this.code = code;
        this.errorType = errorType;
    }

    public String code() {
        return code;
    }

    public ContactsErrorType errorType() {
        return errorType;
    }
}
