package edu.artemiy.chat.app.http;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessagingService;
import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.RoomAccessLevel;
import edu.artemiy.chat.rooms.api.RoomBanRecord;
import edu.artemiy.chat.rooms.api.RoomDetails;
import edu.artemiy.chat.rooms.api.RoomMember;
import edu.artemiy.chat.rooms.api.RoomScope;
import edu.artemiy.chat.rooms.api.RoomSummary;
import edu.artemiy.chat.rooms.api.RoomUserSummary;
import edu.artemiy.chat.rooms.api.RoomVisibility;
import edu.artemiy.chat.rooms.api.RoomsService;

@RestController
@Validated
class RoomsHttpController {

    private final RoomsService roomsService;
    private final MessagingService messagingService;

    RoomsHttpController(RoomsService roomsService, MessagingService messagingService) {
        this.roomsService = roomsService;
        this.messagingService = messagingService;
    }

    @GetMapping("/api/rooms")
    List<RoomSummaryResponse> listRooms(@RequestParam("scope") String scope, Authentication authentication) {
        UUID actorUserId = AuthenticatedHttpUserSupport.userId(authentication);
        RoomScope roomScope = RoomScope.fromHttpValue(scope);
        List<RoomSummary> rooms = roomsService.listRooms(actorUserId, roomScope);
        Map<UUID, Integer> unreadCounts = roomScope == RoomScope.JOINED
            ? unreadCountByChatId(actorUserId, rooms.stream().map(room -> new ChatTargetRef(ChatTargetType.ROOM, room.id())).toList())
            : Map.of();
        return rooms.stream().map(room -> new RoomSummaryResponse(
            room.id(),
            room.name(),
            room.description(),
            room.visibility(),
            room.owner(),
            room.viewerRole(),
            room.memberCount(),
            unreadCounts.getOrDefault(room.id(), 0)
        )).toList();
    }

    @PostMapping("/api/rooms")
    ResponseEntity<RoomSummary> createRoom(@Valid @RequestBody CreateRoomRequest request, Authentication authentication) {
        RoomSummary createdRoom = roomsService.createRoom(
            AuthenticatedHttpUserSupport.userId(authentication),
            request.name(),
            request.description(),
            request.visibility()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRoom);
    }

    @GetMapping("/api/rooms/{roomId}")
    RoomDetailsResponse loadRoomDetails(@PathVariable UUID roomId, Authentication authentication) {
        UUID actorUserId = AuthenticatedHttpUserSupport.userId(authentication);
        RoomDetails details = roomsService.loadRoomDetails(actorUserId, roomId);
        return new RoomDetailsResponse(
            details.id(),
            details.name(),
            details.description(),
            details.visibility(),
            details.accessLevel(),
            details.owner(),
            details.viewerRole(),
            actorUserId,
            details.memberCount(),
            details.canJoin(),
            details.canLeave(),
            details.canInvite(),
            details.canManageAdmins(),
            details.canRemoveMembers(),
            details.canInspectBans(),
            details.canManageBans(),
            details.canDelete(),
            details.members()
        );
    }

    @PostMapping("/api/rooms/{roomId}/join")
    ResponseEntity<Void> joinRoom(@PathVariable UUID roomId, Authentication authentication) {
        roomsService.joinRoom(AuthenticatedHttpUserSupport.userId(authentication), roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/rooms/{roomId}/leave")
    ResponseEntity<Void> leaveRoom(@PathVariable UUID roomId, Authentication authentication) {
        roomsService.leaveRoom(AuthenticatedHttpUserSupport.userId(authentication), roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/rooms/{roomId}/invites")
    ResponseEntity<Void> inviteUser(
        @PathVariable UUID roomId,
        @Valid @RequestBody InviteUserRequest request,
        Authentication authentication
    ) {
        roomsService.inviteUser(AuthenticatedHttpUserSupport.userId(authentication), roomId, request.userId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/rooms/{roomId}/admins/{userId}")
    ResponseEntity<Void> grantAdmin(@PathVariable UUID roomId, @PathVariable UUID userId, Authentication authentication) {
        roomsService.grantAdmin(AuthenticatedHttpUserSupport.userId(authentication), roomId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/rooms/{roomId}/admins/{userId}")
    ResponseEntity<Void> revokeAdmin(@PathVariable UUID roomId, @PathVariable UUID userId, Authentication authentication) {
        roomsService.revokeAdmin(AuthenticatedHttpUserSupport.userId(authentication), roomId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/rooms/{roomId}/members/{userId}")
    ResponseEntity<Void> removeMember(@PathVariable UUID roomId, @PathVariable UUID userId, Authentication authentication) {
        roomsService.removeMember(AuthenticatedHttpUserSupport.userId(authentication), roomId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/rooms/{roomId}/bans")
    List<RoomBanRecord> listBans(@PathVariable UUID roomId, Authentication authentication) {
        return roomsService.listBans(AuthenticatedHttpUserSupport.userId(authentication), roomId);
    }

    @PutMapping("/api/rooms/{roomId}/bans/{userId}")
    ResponseEntity<Void> banUser(
        @PathVariable UUID roomId,
        @PathVariable UUID userId,
        @RequestBody(required = false) BanUserRequest request,
        Authentication authentication
    ) {
        roomsService.banUser(
            AuthenticatedHttpUserSupport.userId(authentication),
            roomId,
            userId,
            request == null ? null : request.reason()
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/rooms/{roomId}/bans/{userId}")
    ResponseEntity<Void> unbanUser(@PathVariable UUID roomId, @PathVariable UUID userId, Authentication authentication) {
        roomsService.unbanUser(AuthenticatedHttpUserSupport.userId(authentication), roomId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/rooms/{roomId}")
    ResponseEntity<Void> deleteRoom(@PathVariable UUID roomId, Authentication authentication) {
        roomsService.deleteRoom(AuthenticatedHttpUserSupport.userId(authentication), roomId);
        return ResponseEntity.noContent().build();
    }

    private record CreateRoomRequest(
        @NotBlank @Size(max = 160) String name,
        String description,
        @NotNull RoomVisibility visibility
    ) {
    }

    private record InviteUserRequest(@NotNull UUID userId) {
    }

    private record BanUserRequest(@Size(max = 1000) String reason) {
    }

    private Map<UUID, Integer> unreadCountByChatId(UUID actorUserId, List<ChatTargetRef> chats) {
        return messagingService.listUnreadCounts(actorUserId, chats).stream()
            .collect(Collectors.toMap(count -> count.chat().id(), count -> count.unreadCount()));
    }

    private record RoomSummaryResponse(
        UUID id,
        String name,
        String description,
        RoomVisibility visibility,
        RoomUserSummary owner,
        MembershipRole viewerRole,
        int memberCount,
        int unreadCount
    ) {
    }

    private record RoomDetailsResponse(
        UUID id,
        String name,
        String description,
        RoomVisibility visibility,
        RoomAccessLevel accessLevel,
        RoomUserSummary owner,
        MembershipRole viewerRole,
        UUID viewerUserId,
        int memberCount,
        boolean canJoin,
        boolean canLeave,
        boolean canInvite,
        boolean canManageAdmins,
        boolean canRemoveMembers,
        boolean canInspectBans,
        boolean canManageBans,
        boolean canDelete,
        List<RoomMember> members
    ) {
    }
}
