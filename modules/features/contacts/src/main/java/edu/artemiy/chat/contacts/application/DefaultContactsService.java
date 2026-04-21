package edu.artemiy.chat.contacts.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.contacts.api.BlockedContactSummary;
import edu.artemiy.chat.contacts.api.ContactUserSummary;
import edu.artemiy.chat.contacts.api.ContactsErrorType;
import edu.artemiy.chat.contacts.api.ContactsException;
import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.ContactsView;
import edu.artemiy.chat.contacts.api.CreateFriendRequestCommand;
import edu.artemiy.chat.contacts.api.DirectDialogSummary;
import edu.artemiy.chat.contacts.api.DirectMessageEligibility;
import edu.artemiy.chat.contacts.api.FriendContactSummary;
import edu.artemiy.chat.contacts.api.FriendRequestSubmission;
import edu.artemiy.chat.contacts.api.FriendRequestSubmissionOutcome;
import edu.artemiy.chat.contacts.api.PendingFriendRequestSummary;
import edu.artemiy.chat.contacts.domain.ContactRelationshipState;
import edu.artemiy.chat.contacts.domain.OrderedContactPair;
import edu.artemiy.chat.contacts.spi.ContactsPersistencePort;
import edu.artemiy.chat.contacts.spi.DuplicateDirectDialogException;
import edu.artemiy.chat.contacts.spi.DuplicateFriendshipException;
import edu.artemiy.chat.contacts.spi.DuplicatePendingFriendRequestException;
import edu.artemiy.chat.contacts.spi.NewDirectDialogRecord;
import edu.artemiy.chat.contacts.spi.NewFriendshipRecord;
import edu.artemiy.chat.contacts.spi.NewFriendshipRequestRecord;
import edu.artemiy.chat.contacts.spi.NewUserBlockRecord;
import edu.artemiy.chat.contacts.spi.StoredBlockedContactEntry;
import edu.artemiy.chat.contacts.spi.StoredContactUser;
import edu.artemiy.chat.contacts.spi.StoredDirectDialog;
import edu.artemiy.chat.contacts.spi.StoredFriendContactEntry;
import edu.artemiy.chat.contacts.spi.StoredFriendship;
import edu.artemiy.chat.contacts.spi.StoredFriendshipRequest;
import edu.artemiy.chat.contacts.spi.StoredPendingFriendRequestEntry;
import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.core.kernel.UsernameRules;

@Service
public class DefaultContactsService implements ContactsService {

    private final ClockPort clockPort;
    private final ContactsPersistencePort contactsPersistencePort;

    public DefaultContactsService(ClockPort clockPort, ContactsPersistencePort contactsPersistencePort) {
        this.clockPort = clockPort;
        this.contactsPersistencePort = contactsPersistencePort;
    }

    @Override
    @Transactional(readOnly = true)
    public ContactsView listContacts(UUID actorUserId) {
        requireActiveUser(actorUserId);
        return new ContactsView(
            contactsPersistencePort.listFriends(actorUserId).stream()
                .map(this::toFriendSummary)
                .toList(),
            contactsPersistencePort.listInboundPendingRequests(actorUserId).stream()
                .map(this::toPendingSummary)
                .toList(),
            contactsPersistencePort.listOutboundPendingRequests(actorUserId).stream()
                .map(this::toPendingSummary)
                .toList(),
            contactsPersistencePort.listBlockedUsers(actorUserId).stream()
                .map(this::toBlockedSummary)
                .toList()
        );
    }

    @Override
    @Transactional
    public FriendRequestSubmission createFriendRequest(UUID actorUserId, CreateFriendRequestCommand command) {
        StoredContactUser actor = requireActiveUser(actorUserId);
        StoredContactUser target = resolveTargetUser(command);
        if (actor.id().equals(target.id())) {
            throw new ContactsException(
                "contacts.friend_request_self",
                "You cannot send a friend request to yourself.",
                ContactsErrorType.BAD_REQUEST
            );
        }

        contactsPersistencePort.lockUserPair(actor.id(), target.id());
        ContactRelationshipState relationshipState = loadRelationshipState(actor.id(), target.id());
        if (relationshipState.activeFriendship()) {
            throw new ContactsException(
                "contacts.friendship_exists",
                "Users are already friends.",
                ContactsErrorType.CONFLICT
            );
        }
        if (!relationshipState.allowsNewFriendRequest()) {
            throw new ContactsException(
                "contacts.friend_request_blocked",
                "Friend requests are unavailable while a block exists between these users.",
                ContactsErrorType.CONFLICT
            );
        }
        if (contactsPersistencePort.findPendingFriendRequest(actor.id(), target.id()).isPresent()) {
            throw new ContactsException(
                "contacts.friend_request_pending",
                "A pending friend request already exists for this user.",
                ContactsErrorType.CONFLICT
            );
        }

        Instant now = clockPort.now();
        StoredFriendshipRequest reverseRequest = contactsPersistencePort.findPendingFriendRequest(target.id(), actor.id()).orElse(null);
        if (reverseRequest != null) {
            StoredFriendship friendship = acceptLockedRequest(reverseRequest, now);
            return new FriendRequestSubmission(
                FriendRequestSubmissionOutcome.AUTO_ACCEPTED,
                null,
                friendship.id(),
                toUserSummary(target)
            );
        }

        try {
            StoredFriendshipRequest createdRequest = contactsPersistencePort.createFriendshipRequest(new NewFriendshipRequestRecord(
                UUID.randomUUID(),
                actor.id(),
                target.id(),
                normalizeOptional(command.messageText()),
                now
            ));
            return new FriendRequestSubmission(
                FriendRequestSubmissionOutcome.REQUEST_CREATED,
                createdRequest.id(),
                null,
                toUserSummary(target)
            );
        }
        catch (DuplicatePendingFriendRequestException exception) {
            throw new ContactsException(
                "contacts.friend_request_pending",
                "A pending friend request already exists for this user.",
                ContactsErrorType.CONFLICT
            );
        }
    }

    @Override
    @Transactional
    public void acceptFriendRequest(UUID actorUserId, UUID requestId) {
        requireActiveUser(actorUserId);
        StoredFriendshipRequest request = contactsPersistencePort.findFriendshipRequest(requestId)
            .orElseThrow(this::requestNotFoundException);
        contactsPersistencePort.lockUserPair(request.requesterUserId(), request.recipientUserId());
        StoredFriendshipRequest pendingRequest = contactsPersistencePort.findPendingFriendRequestForRecipient(requestId, actorUserId)
            .orElseThrow(this::requestNotFoundException);
        acceptLockedRequest(pendingRequest, clockPort.now());
    }

    @Override
    @Transactional
    public void rejectFriendRequest(UUID actorUserId, UUID requestId) {
        requireActiveUser(actorUserId);
        StoredFriendshipRequest request = contactsPersistencePort.findFriendshipRequest(requestId)
            .orElseThrow(this::requestNotFoundException);
        contactsPersistencePort.lockUserPair(request.requesterUserId(), request.recipientUserId());
        StoredFriendshipRequest pendingRequest = contactsPersistencePort.findPendingFriendRequestForRecipient(requestId, actorUserId)
            .orElseThrow(this::requestNotFoundException);
        contactsPersistencePort.markFriendshipRequestRejected(pendingRequest.id(), clockPort.now());
    }

    @Override
    @Transactional
    public void removeFriend(UUID actorUserId, UUID userId) {
        StoredContactUser actor = requireActiveUser(actorUserId);
        StoredContactUser target = requireExistingUser(userId, "contacts.target_not_found", "Target user was not found.");
        if (actor.id().equals(target.id())) {
            throw new ContactsException(
                "contacts.friendship_remove_self",
                "You cannot remove yourself from contacts.",
                ContactsErrorType.BAD_REQUEST
            );
        }

        contactsPersistencePort.lockUserPair(actor.id(), target.id());
        StoredFriendship friendship = findFriendship(actor.id(), target.id())
            .orElseThrow(() -> new ContactsException(
                "contacts.friendship_not_found",
                "Friendship was not found.",
                ContactsErrorType.NOT_FOUND
            ));
        contactsPersistencePort.deleteFriendship(friendship.id());
    }

    @Override
    @Transactional
    public void blockUser(UUID actorUserId, UUID userId) {
        StoredContactUser actor = requireActiveUser(actorUserId);
        StoredContactUser target = requireExistingUser(userId, "contacts.target_not_found", "Target user was not found.");
        if (actor.id().equals(target.id())) {
            throw new ContactsException(
                "contacts.block_self",
                "You cannot block yourself.",
                ContactsErrorType.BAD_REQUEST
            );
        }

        contactsPersistencePort.lockUserPair(actor.id(), target.id());
        ContactRelationshipState relationshipState = loadRelationshipState(actor.id(), target.id());
        if (relationshipState.blockedByActor()) {
            return;
        }

        Instant now = clockPort.now();
        findFriendship(actor.id(), target.id()).ifPresent(friendship -> contactsPersistencePort.deleteFriendship(friendship.id()));
        contactsPersistencePort.rejectPendingFriendRequestsBetween(actor.id(), target.id(), now);
        contactsPersistencePort.createUserBlock(new NewUserBlockRecord(
            UUID.randomUUID(),
            actor.id(),
            target.id(),
            now
        ));
    }

    @Override
    @Transactional
    public void unblockUser(UUID actorUserId, UUID userId) {
        StoredContactUser actor = requireActiveUser(actorUserId);
        StoredContactUser target = requireExistingUser(userId, "contacts.target_not_found", "Target user was not found.");
        if (actor.id().equals(target.id())) {
            throw new ContactsException(
                "contacts.unblock_self",
                "You cannot unblock yourself.",
                ContactsErrorType.BAD_REQUEST
            );
        }

        contactsPersistencePort.lockUserPair(actor.id(), target.id());
        contactsPersistencePort.deleteUserBlock(actor.id(), target.id());
    }

    @Override
    @Transactional(readOnly = true)
    public DirectMessageEligibility evaluateDirectMessageEligibility(UUID actorUserId, UUID userId) {
        StoredContactUser actor = requireActiveUser(actorUserId);
        StoredContactUser target = requireExistingUser(userId, "contacts.target_not_found", "Target user was not found.");
        if (actor.id().equals(target.id())) {
            throw new ContactsException(
                "contacts.direct_message_self",
                "You cannot open a direct dialog with yourself.",
                ContactsErrorType.BAD_REQUEST
            );
        }
        return loadRelationshipState(actor.id(), target.id()).directMessageEligibility();
    }

    @Override
    @Transactional
    public DirectDialogSummary ensureDirectDialog(UUID actorUserId, UUID userId) {
        StoredContactUser actor = requireActiveUser(actorUserId);
        StoredContactUser target = requireActiveUser(userId, "contacts.target_not_found", "Target user was not found.");
        if (actor.id().equals(target.id())) {
            throw new ContactsException(
                "contacts.direct_message_self",
                "You cannot open a direct dialog with yourself.",
                ContactsErrorType.BAD_REQUEST
            );
        }

        contactsPersistencePort.lockUserPair(actor.id(), target.id());
        DirectMessageEligibility eligibility = loadRelationshipState(actor.id(), target.id()).directMessageEligibility();
        if (eligibility != DirectMessageEligibility.ELIGIBLE) {
            throw directDialogIneligibleException(eligibility);
        }

        OrderedContactPair pair = OrderedContactPair.of(actor.id(), target.id());
        StoredDirectDialog existingDialog = contactsPersistencePort.findDirectDialog(pair.lowUserId(), pair.highUserId()).orElse(null);
        if (existingDialog != null) {
            return toDirectDialogSummary(existingDialog, target, false);
        }

        try {
            StoredDirectDialog createdDialog = contactsPersistencePort.createDirectDialog(new NewDirectDialogRecord(
                UUID.randomUUID(),
                pair.lowUserId(),
                pair.highUserId(),
                clockPort.now()
            ));
            return toDirectDialogSummary(createdDialog, target, true);
        }
        catch (DuplicateDirectDialogException exception) {
            StoredDirectDialog reusedDialog = contactsPersistencePort.findDirectDialog(pair.lowUserId(), pair.highUserId())
                .orElseThrow(() -> exception);
            return toDirectDialogSummary(reusedDialog, target, false);
        }
    }

    @Override
    @Transactional
    public void handleAccountDeleted(UUID userId) {
        contactsPersistencePort.deleteRelationshipsForDeletedUser(userId);
    }

    private StoredFriendship acceptLockedRequest(StoredFriendshipRequest request, Instant respondedAt) {
        StoredFriendship friendship = findFriendship(request.requesterUserId(), request.recipientUserId()).orElseGet(() -> {
            OrderedContactPair pair = OrderedContactPair.of(request.requesterUserId(), request.recipientUserId());
            try {
                return contactsPersistencePort.createFriendship(new NewFriendshipRecord(
                    UUID.randomUUID(),
                    pair.lowUserId(),
                    pair.highUserId(),
                    respondedAt
                ));
            }
            catch (DuplicateFriendshipException exception) {
                return findFriendship(request.requesterUserId(), request.recipientUserId())
                    .orElseThrow(() -> exception);
            }
        });
        contactsPersistencePort.markFriendshipRequestAccepted(request.id(), respondedAt);
        return friendship;
    }

    private ContactsException requestNotFoundException() {
        return new ContactsException(
            "contacts.friend_request_not_found",
            "Friend request was not found.",
            ContactsErrorType.NOT_FOUND
        );
    }

    private StoredContactUser requireActiveUser(UUID userId) {
        return requireActiveUser(userId, "contacts.user_not_found", "User account was not found.");
    }

    private StoredContactUser requireExistingUser(UUID userId, String code, String message) {
        return contactsPersistencePort.findUserById(userId)
            .orElseThrow(() -> new ContactsException(code, message, ContactsErrorType.NOT_FOUND));
    }

    private StoredContactUser requireActiveUser(UUID userId, String code, String message) {
        return contactsPersistencePort.findActiveUserById(userId)
            .orElseThrow(() -> new ContactsException(code, message, ContactsErrorType.NOT_FOUND));
    }

    private StoredContactUser resolveTargetUser(CreateFriendRequestCommand command) {
        boolean hasUserId = command.userId() != null;
        boolean hasUsername = command.username() != null && !command.username().isBlank();
        if (!hasUserId && !hasUsername) {
            throw new ContactsException(
                "contacts.friend_request_target_required",
                "Either userId or username is required.",
                ContactsErrorType.BAD_REQUEST
            );
        }
        if (hasUserId && hasUsername) {
            throw new ContactsException(
                "contacts.friend_request_target_ambiguous",
                "Provide either userId or username, not both.",
                ContactsErrorType.BAD_REQUEST
            );
        }
        if (hasUserId) {
            return contactsPersistencePort.findActiveUserById(command.userId())
                .orElseThrow(() -> new ContactsException(
                    "contacts.target_not_found",
                    "Target user was not found.",
                    ContactsErrorType.NOT_FOUND
                ));
        }
        String normalizedUsername;
        try {
            normalizedUsername = UsernameRules.normalize(command.username());
        }
        catch (UsernameRules.InvalidUsernameException exception) {
            throw new ContactsException(
                exception.reason() == UsernameRules.InvalidUsernameReason.MISSING
                    ? "contacts.friend_request_target_required"
                    : "contacts.friend_request_target_invalid",
                exception.reason() == UsernameRules.InvalidUsernameReason.MISSING
                    ? "Target username is required."
                    : exception.getMessage(),
                ContactsErrorType.BAD_REQUEST
            );
        }
        return contactsPersistencePort.findActiveUserByUsername(normalizedUsername)
            .orElseThrow(() -> new ContactsException(
                "contacts.target_not_found",
                "Target user was not found.",
                ContactsErrorType.NOT_FOUND
            ));
    }

    private java.util.Optional<StoredFriendship> findFriendship(UUID firstUserId, UUID secondUserId) {
        OrderedContactPair pair = OrderedContactPair.of(firstUserId, secondUserId);
        return contactsPersistencePort.findFriendship(pair.lowUserId(), pair.highUserId());
    }

    private ContactRelationshipState loadRelationshipState(UUID actorUserId, UUID otherUserId) {
        return new ContactRelationshipState(
            findFriendship(actorUserId, otherUserId).isPresent(),
            contactsPersistencePort.findUserBlock(actorUserId, otherUserId).isPresent(),
            contactsPersistencePort.findUserBlock(otherUserId, actorUserId).isPresent()
        );
    }

    private ContactsException directDialogIneligibleException(DirectMessageEligibility eligibility) {
        return switch (eligibility) {
            case NOT_FRIENDS -> new ContactsException(
                "contacts.direct_dialog_not_friends",
                "Direct dialogs require an active friendship.",
                ContactsErrorType.CONFLICT
            );
            case BLOCKED -> new ContactsException(
                "contacts.direct_dialog_blocked",
                "Direct dialogs are unavailable while a block exists between these users.",
                ContactsErrorType.CONFLICT
            );
            case ELIGIBLE -> throw new IllegalArgumentException("Eligibility must be ineligible for this error path.");
        };
    }

    private FriendContactSummary toFriendSummary(StoredFriendContactEntry entry) {
        return new FriendContactSummary(
            entry.friendshipId(),
            toUserSummary(entry.otherUserId(), entry.otherUsername(), entry.otherDisplayName(), entry.otherDeleted()),
            entry.friendsSince()
        );
    }

    private PendingFriendRequestSummary toPendingSummary(StoredPendingFriendRequestEntry entry) {
        return new PendingFriendRequestSummary(
            entry.requestId(),
            toUserSummary(entry.otherUserId(), entry.otherUsername(), entry.otherDisplayName(), entry.otherDeleted()),
            entry.messageText(),
            entry.createdAt()
        );
    }

    private BlockedContactSummary toBlockedSummary(StoredBlockedContactEntry entry) {
        return new BlockedContactSummary(
            toUserSummary(entry.otherUserId(), entry.otherUsername(), entry.otherDisplayName(), entry.otherDeleted()),
            entry.blockedAt()
        );
    }

    private DirectDialogSummary toDirectDialogSummary(StoredDirectDialog dialog, StoredContactUser participant, boolean created) {
        return new DirectDialogSummary(
            dialog.id(),
            toUserSummary(participant),
            dialog.createdAt(),
            created
        );
    }

    private ContactUserSummary toUserSummary(StoredContactUser user) {
        return toUserSummary(user.id(), user.username(), user.displayName(), user.deleted());
    }

    private ContactUserSummary toUserSummary(UUID userId, String username, String displayName, boolean deleted) {
        return new ContactUserSummary(userId, username, displayName, deleted);
    }

    private static String requireTrimmed(String value, String message) {
        if (value == null) {
            throw new ContactsException("contacts.request_invalid", message, ContactsErrorType.BAD_REQUEST);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ContactsException("contacts.request_invalid", message, ContactsErrorType.BAD_REQUEST);
        }
        return trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
