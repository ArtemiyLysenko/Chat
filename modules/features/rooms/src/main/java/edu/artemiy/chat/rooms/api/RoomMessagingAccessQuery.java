package edu.artemiy.chat.rooms.api;

import java.util.UUID;

public interface RoomMessagingAccessQuery {

    RoomMessagingAccess evaluateRoomMessagingAccess(UUID actorUserId, UUID roomId);
}
