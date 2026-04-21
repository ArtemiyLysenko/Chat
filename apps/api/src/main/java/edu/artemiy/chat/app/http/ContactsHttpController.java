package edu.artemiy.chat.app.http;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
import org.springframework.web.bind.annotation.RestController;

import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.ContactsView;
import edu.artemiy.chat.contacts.api.CreateFriendRequestCommand;
import edu.artemiy.chat.contacts.api.DirectDialogSummary;
import edu.artemiy.chat.contacts.api.FriendRequestSubmission;
import edu.artemiy.chat.contacts.api.FriendRequestSubmissionOutcome;

@RestController
@Validated
class ContactsHttpController {

    private final ContactsService contactsService;

    ContactsHttpController(ContactsService contactsService) {
        this.contactsService = contactsService;
    }

    @GetMapping("/api/contacts")
    ContactsView listContacts(Authentication authentication) {
        return contactsService.listContacts(AuthenticatedHttpUserSupport.userId(authentication));
    }

    @PostMapping("/api/friend-requests")
    ResponseEntity<FriendRequestSubmission> createFriendRequest(
        @Valid @RequestBody CreateFriendRequestRequest request,
        Authentication authentication
    ) {
        FriendRequestSubmission submission = contactsService.createFriendRequest(
            AuthenticatedHttpUserSupport.userId(authentication),
            new CreateFriendRequestCommand(request.userId(), request.username(), request.messageText())
        );
        HttpStatus status = submission.outcome() == FriendRequestSubmissionOutcome.REQUEST_CREATED
            ? HttpStatus.CREATED
            : HttpStatus.OK;
        return ResponseEntity.status(status).body(submission);
    }

    @PostMapping("/api/friend-requests/{requestId}/accept")
    ResponseEntity<Void> acceptFriendRequest(@PathVariable UUID requestId, Authentication authentication) {
        contactsService.acceptFriendRequest(AuthenticatedHttpUserSupport.userId(authentication), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/friend-requests/{requestId}/reject")
    ResponseEntity<Void> rejectFriendRequest(@PathVariable UUID requestId, Authentication authentication) {
        contactsService.rejectFriendRequest(AuthenticatedHttpUserSupport.userId(authentication), requestId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/contacts/{userId}")
    ResponseEntity<Void> removeFriend(@PathVariable UUID userId, Authentication authentication) {
        contactsService.removeFriend(AuthenticatedHttpUserSupport.userId(authentication), userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/blocks/{userId}")
    ResponseEntity<Void> blockUser(@PathVariable UUID userId, Authentication authentication) {
        contactsService.blockUser(AuthenticatedHttpUserSupport.userId(authentication), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/blocks/{userId}")
    ResponseEntity<Void> unblockUser(@PathVariable UUID userId, Authentication authentication) {
        contactsService.unblockUser(AuthenticatedHttpUserSupport.userId(authentication), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/direct-dialogs/{userId}")
    ResponseEntity<DirectDialogSummary> ensureDirectDialog(@PathVariable UUID userId, Authentication authentication) {
        DirectDialogSummary dialog = contactsService.ensureDirectDialog(AuthenticatedHttpUserSupport.userId(authentication), userId);
        HttpStatus status = dialog.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(dialog);
    }

    private record CreateFriendRequestRequest(
        UUID userId,
        @Size(max = 32) String username,
        String messageText
    ) {

        @AssertTrue(message = "Either userId or username is required.")
        private boolean hasTarget() {
            return userId != null || (username != null && !username.isBlank());
        }

        @AssertTrue(message = "Provide either userId or username, not both.")
        private boolean hasSingleTarget() {
            return userId == null || username == null || username.isBlank();
        }
    }
}
