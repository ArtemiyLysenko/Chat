package edu.artemiy.chat.app.http;

import java.util.List;
import java.util.UUID;

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

import edu.artemiy.chat.rooms.api.RoomBanRecord;
import edu.artemiy.chat.rooms.api.RoomDetails;
import edu.artemiy.chat.rooms.api.RoomScope;
import edu.artemiy.chat.rooms.api.RoomSummary;
import edu.artemiy.chat.rooms.api.RoomVisibility;
import edu.artemiy.chat.rooms.api.RoomsService;

@RestController
@Validated
class RoomsHttpController {

    private final RoomsService roomsService;

    RoomsHttpController(RoomsService roomsService) {
        this.roomsService = roomsService;
    }

    @GetMapping("/api/rooms")
    List<RoomSummary> listRooms(@RequestParam("scope") String scope, Authentication authentication) {
        return roomsService.listRooms(AuthenticatedHttpUserSupport.userId(authentication), RoomScope.fromHttpValue(scope));
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
    RoomDetails loadRoomDetails(@PathVariable UUID roomId, Authentication authentication) {
        return roomsService.loadRoomDetails(AuthenticatedHttpUserSupport.userId(authentication), roomId);
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
}
