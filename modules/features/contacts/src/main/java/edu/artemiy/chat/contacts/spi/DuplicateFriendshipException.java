package edu.artemiy.chat.contacts.spi;

public class DuplicateFriendshipException extends RuntimeException {

    public DuplicateFriendshipException(Throwable cause) {
        super(cause);
    }
}
