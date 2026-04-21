package edu.artemiy.chat.rooms.api;

import java.util.List;
import java.util.UUID;

public interface RoomsService {

    RoomSummary createRoom(UUID actorUserId, String name, String description, RoomVisibility visibility);

    List<RoomSummary> listRooms(UUID actorUserId, RoomScope scope);

    RoomDetails loadRoomDetails(UUID actorUserId, UUID roomId);

    void inviteUser(UUID actorUserId, UUID roomId, UUID targetUserId);

    void joinRoom(UUID actorUserId, UUID roomId);

    void leaveRoom(UUID actorUserId, UUID roomId);

    void deleteRoom(UUID actorUserId, UUID roomId);

    void grantAdmin(UUID actorUserId, UUID roomId, UUID targetUserId);

    void revokeAdmin(UUID actorUserId, UUID roomId, UUID targetUserId);

    void removeMember(UUID actorUserId, UUID roomId, UUID targetUserId);

    List<RoomBanRecord> listBans(UUID actorUserId, UUID roomId);

    void banUser(UUID actorUserId, UUID roomId, UUID targetUserId, String reason);

    void unbanUser(UUID actorUserId, UUID roomId, UUID targetUserId);
}
