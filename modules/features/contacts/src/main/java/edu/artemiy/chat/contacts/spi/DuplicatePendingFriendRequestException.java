package edu.artemiy.chat.contacts.spi;

public class DuplicatePendingFriendRequestException extends RuntimeException {

    public DuplicatePendingFriendRequestException(Throwable cause) {
        super(cause);
    }
}
