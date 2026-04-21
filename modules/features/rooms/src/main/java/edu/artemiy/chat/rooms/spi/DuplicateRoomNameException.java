package edu.artemiy.chat.rooms.spi;

public class DuplicateRoomNameException extends RuntimeException {

    public DuplicateRoomNameException(Throwable cause) {
        super(cause);
    }
}
