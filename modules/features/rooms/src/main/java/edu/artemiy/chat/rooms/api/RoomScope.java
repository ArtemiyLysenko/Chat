package edu.artemiy.chat.rooms.api;

public enum RoomScope {
    JOINED,
    CATALOG;

    public static RoomScope fromHttpValue(String value) {
        if (value == null) {
            throw new RoomsException("rooms.scope_missing", "Room scope is required.", RoomsErrorType.BAD_REQUEST);
        }
        return switch (value.trim().toLowerCase()) {
            case "joined" -> JOINED;
            case "catalog" -> CATALOG;
            default -> throw new RoomsException("rooms.scope_invalid", "Room scope must be either joined or catalog.", RoomsErrorType.BAD_REQUEST);
        };
    }
}
