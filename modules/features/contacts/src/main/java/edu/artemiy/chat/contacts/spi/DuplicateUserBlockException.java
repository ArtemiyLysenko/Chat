package edu.artemiy.chat.contacts.spi;

public class DuplicateUserBlockException extends RuntimeException {

    public DuplicateUserBlockException(Throwable cause) {
        super(cause);
    }
}
